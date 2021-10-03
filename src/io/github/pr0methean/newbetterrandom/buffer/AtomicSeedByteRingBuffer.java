package io.github.pr0methean.newbetterrandom.buffer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe, garbage-free byte ring buffer designed to satisfy the following invariants:
 * <ol>
 *   <li>No byte address is read before it has been written.</li>
 *   <li>Each byte address is read at most once between writes.</li>
 *   <li>Each byte that is read has the same value as a byte that was written.</li>
 * </ol>
 * <p>The intended use case is reseeding of pseudorandom number generators (PRNGs), to prevent
 * results of complex programs from being influenced by subtle patterns in the PRNG's output. For
 * example, in a simulation program, a handful of threads may read from truly-random sources such as
 * a Unix {@code /dev/random} and call {@link #write(byte[], int, int)} to deliver seed material,
 * while each thread running the simulation calls {@link #poll(byte[], int, int)} and, when it
 * succeeds in fetching enough bytes, either replaces its PRNG with a new one obtained by calling
 * {@link java.util.random.RandomGeneratorFactory#create(byte[])} or, in the case of a
 * {@link java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator} whose period far exceeds
 * the amount of output expected over the simulation's running time, jumping by a distance equal to
 * the seed.</p>
 */
public class AtomicSeedByteRingBuffer {

  public long getByteSize() {
    return byteSize;
  }

  protected final long byteSize;
  protected final long bitMask;

  protected final AtomicLong bytesStartedWriting = new AtomicLong();
  protected final AtomicLong bytesFinishedWriting = new AtomicLong();
  protected final AtomicLong bytesStartedReading = new AtomicLong();
  protected final ByteBuffer buffer;

  void checkInternalInvariants() {
    {
      final long finishedWriting = bytesFinishedWriting.get();
      final long startedWriting = bytesStartedWriting.get();
      if (startedWriting < finishedWriting) {
        throw new IllegalStateException("bytesStartedWriting < bytesFinishedWriting");
      }
    }
    {
      final long finishedWritingTime1 = bytesFinishedWriting.get();
      final long finishedWritingTime2 = bytesFinishedWriting.get();
      if (finishedWritingTime1 > finishedWritingTime2) {
        throw new IllegalStateException("bytesFinishedWriting is non-monotonic");
      }
    }
  }

  public AtomicSeedByteRingBuffer(final int byteSize) {
    this(ByteBuffer.allocateDirect(byteSize));
  }

  AtomicSeedByteRingBuffer(final ByteBuffer buffer) {
    this.buffer = buffer;
    this.byteSize = buffer.capacity();
    if (Long.bitCount(byteSize) != 1) {
      throw new IllegalArgumentException("Buffer size must be a power of 2");
    }
    bitMask = this.byteSize - 1;
  }

  /**
   * Nonblocking write of up to {@code desiredLength} bytes.
   *
   * @param source the array to copy from
   * @param start the first index to copy from
   * @param desiredLength the maximum number of bytes to write; can be more than this buffer's
   *        capacity, but no more than the capacity will actually be written
   * @return the number of bytes actually written
   */
  public int offer(final byte[] source, final int start, int desiredLength) {
    if (desiredLength < 0) {
      throw new IllegalArgumentException("desiredLength can't be negative");
    }
    if (desiredLength == 0) {
      return 0;
    }
    if (desiredLength > byteSize) {
      desiredLength = (int) byteSize;
    }
    int actualLength = 0;
    final long writeStart = bytesStartedWriting.getAndAdd(desiredLength);
    final long writeLimit = bytesStartedReading.get() + byteSize;
    try {
      if (writeStart >= writeLimit) {
        return 0; // Buffer is full
      }
      if (writeStart + desiredLength > writeLimit) {
        actualLength = (int) (writeLimit - writeStart); // Full write would overfill buffer
      } else {
        actualLength = desiredLength; // Room for full write
      }
      final int writeStartIndex = (int) (writeStart & bitMask);
      final int writeEndIndex = (int) ((writeStart + actualLength) & bitMask);
      if (writeEndIndex <= writeStartIndex) {
        final int lengthBeforeWrap = (int) (byteSize - writeStartIndex);
        final int lengthAfterWrap = actualLength - lengthBeforeWrap;
        buffer.put(writeStartIndex, source, start, lengthBeforeWrap);
        buffer.put(0, source, start + lengthBeforeWrap, lengthAfterWrap);
      } else {
        buffer.put(writeStartIndex, source, start, actualLength);
      }
      if (!bytesFinishedWriting.compareAndSet(writeStart, writeStart + actualLength)) {
        /*
         * Must report that the write failed, to prevent the following scenario:
         *
         * 1. Thread W1 starts writing bytes 0..20. bytesStartedWriting set to 20.
         * 2. Thread W2 starts writing bytes 20..30. bytesStartedWriting set to 30.
         * 3. Thread W2 finishes. bytesFinishedWriting set to 10.
         * 4. Thread R starts reading bytes 0..10, even though they still aren't written.
         */
        actualLength = 0;
      }
      return actualLength;
    } finally {
      if (actualLength != desiredLength) {
        bytesStartedWriting.getAndAdd(actualLength - desiredLength);
      }
    }
  }

  /**
   * Nonblocking read of up to {@code desiredLength} bytes.
   *
   * @param dest the array to copy into
   * @param start the first index to copy to
   * @param desiredLength the maximum number of bytes to read; can be more than this buffer's
   *        capacity, but no more than the capacity will actually be written
   * @return the number of bytes actually read
   */
  public int poll(final byte[] dest, final int start, int desiredLength) {
    if (desiredLength < 0) {
      throw new IllegalArgumentException("desiredLength can't be negative");
    }
    if (desiredLength == 0) {
      return 0;
    }
    if (desiredLength > byteSize) {
      desiredLength = (int) byteSize;
    }
    int actualLength = 0;
    final long readStart = bytesStartedReading.getAndAdd(desiredLength);
    final long written = bytesFinishedWriting.get();
    try {
      if (readStart >= written) {
        return 0;
      }
      if (readStart + desiredLength > written) {
        actualLength = (int) (written - readStart);
      } else {
        actualLength = desiredLength;
      }
      final int readStartIndex = (int) (readStart & bitMask);
      final int endIndex = (int) ((readStart + actualLength) & bitMask);
      if (endIndex <= readStartIndex) {
        final int lengthBeforeWrap = (int) (byteSize - readStartIndex);
        final int lengthAfterWrap = actualLength - lengthBeforeWrap;
        buffer.get(readStartIndex, dest, start, lengthBeforeWrap);
        buffer.get(0, dest, start + lengthBeforeWrap, lengthAfterWrap);
      } else {
        buffer.get(readStartIndex, dest, start, actualLength);
      }
    } finally {
      if (actualLength < desiredLength) {
        bytesStartedReading.getAndAdd(actualLength - desiredLength);
      }
    }
    return actualLength;
  }

  public boolean pollAllOrNone(final byte[] dest, final int start, int desiredLength) {
    if (desiredLength > byteSize) {
      return false; // Read of more than capacity will never succeed
    }
    int actuallyRead = poll(dest, start, desiredLength);
    if (actuallyRead >= desiredLength) {
      return true;
    }
    if (actuallyRead > 0) {
      offer(dest, start, actuallyRead); // Toss back what we haven't used
    }
    return false;
  }

  /**
   * Blocking write of exactly {@code length} bytes.
   * Deadlock-free provided that enough bytes will eventually be polled for.
   *
   * @param source the array to copy from
   * @param start the first index to copy from
   * @param length the number of bytes to write; may be more than this buffer's capacity
   * @throws InterruptedException if interrupted white waiting to write
   */
  public void write(final byte[] source, final int start, final int length) throws InterruptedException {
    writeWeak(new WeakReference<>(this), source, start, length);
  }

  /**
   * Blocking write of exactly {@code length} bytes; returns early if the buffer dies before all
   * bytes have been written. Deadlock-free provided that enough bytes will eventually be polled for
   * or the buffer will eventually die.
   *
   * @param bufferWeakReference weak reference to the buffer to write to
   * @param source the array to copy from
   * @param start the first index to copy from
   * @param length the number of bytes to write; may be more than this buffer's capacity
   * @throws InterruptedException if interrupted white waiting to write
   */
  public static void writeWeak(WeakReference<AtomicSeedByteRingBuffer> bufferWeakReference,
      final byte[] source, final int start, final int length) throws InterruptedException {
    int written = 0;
    while (written < length) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      AtomicSeedByteRingBuffer buffer = bufferWeakReference.get();
      if (buffer == null) {
        return;
      }
      final int writtenThisIteration = buffer.offer(source, start + written, length - written);
      if (writtenThisIteration == 0) {
        Thread.onSpinWait();
      }
      written += writtenThisIteration;
    }
  }

  /**
   * Blocking read of exactly {@code length} bytes.
   * Deadlock-free provided that enough bytes will eventually be offered. Starvation is possible if
   * the number of writing threads exceeds the number of physical CPU cores.
   *
   * @param dest the array to copy into
   * @param start the first index to copy to
   * @param length the number of bytes to read; may be more than this buffer's capacity
   * @throws InterruptedException if interrupted while waiting to read
   */
  public void read(final byte[] dest, final int start, final int length) throws InterruptedException {
    int read = 0;
    while (read < length) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      final int readThisIteration = poll(dest, start + read, length - read);
      if (readThisIteration == 0) {
        Thread.onSpinWait();
      }
      read += readThisIteration;
    }
  }
}
