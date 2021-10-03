package io.github.pr0methean.newbetterrandom.buffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AtomicSeedByteRingBufferTest {

  public static final byte[] BYTES =
      {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
  public static final int SIZE = BYTES.length;

  private static int[] byteArrayToIntArray(final byte[] bytes) {
    final int[] intBytes = new int[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      intBytes[i] = bytes[i];
    }
    return intBytes;
  }

  @Test
  public void testWrite() {
    final byte[] bytes = new byte[]{0,1,2,3,4,5,6,7};
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(bytes.length);
    final int bytesWritten = buffer.offer(bytes, 0, bytes.length);
    assertEquals(bytes.length, bytesWritten);
    final byte[] outBytes = new byte[8];
    buffer.buffer.get(0, outBytes);
    assertArrayEquals(bytes, outBytes);
  }

  @Test
  public void testOfferThenPollSingleCall() {
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(1 << 5);
    assertEquals(SIZE, buffer.offer(BYTES, 0, SIZE));
    final byte[] output = new byte[SIZE];
    assertEquals(SIZE, buffer.poll(output, 0, SIZE));
    assertArrayEquals(BYTES, output);
  }

  @Test
  public void testOfferThenPollMultipleCallsSameThread() {
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(1 << 5);
    final int writeSplitPoint = 3;
    checkTwoWrites(buffer, writeSplitPoint, BYTES);
    final byte[] output = new byte[SIZE];
    final int readSplitPoint = 13;
    assertEquals(readSplitPoint, buffer.poll(output, 0, readSplitPoint));
    buffer.checkInternalInvariants();
    assertEquals(SIZE - readSplitPoint, buffer.poll(output, readSplitPoint, SIZE - readSplitPoint));
    buffer.checkInternalInvariants();
    assertArrayEquals(BYTES, output);
  }

  private static void checkTwoWrites(final AtomicSeedByteRingBuffer buffer, final int writeSplitPoint, final byte[] input) {
    assertEquals(writeSplitPoint, buffer.offer(input, 0, writeSplitPoint));
    buffer.checkInternalInvariants();
    assertEquals(input.length - writeSplitPoint, buffer
        .offer(input, writeSplitPoint, input.length - writeSplitPoint));
    buffer.checkInternalInvariants();
  }

  @Test
  public void testOffByOneRegression() {
    final int size = 1 << 5;
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(size);
    final int writeSplitPoint = size - 1;
    checkTwoWrites(buffer, writeSplitPoint, new byte[size]);
  }

  @Test
  public void testWriteWhenFull() {
    final int size = 1 << 4;
    final byte[] dummyInput = new byte[size];
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(size);
    buffer.offer(dummyInput, 0, size);
    assertEquals(0, buffer.offer(dummyInput, 0, size));
  }

  @Test
  public void testReadWhenEmpty() {
    final int size = 1 << 4;
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(size);
    assertEquals(0, buffer.poll(new byte[size], 0, size));
  }

  @Test
  public void testWriteBlocking() throws InterruptedException {
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(1 << 5);
    final ForkJoinPool pool = new ForkJoinPool();
    final ForkJoinTask<?> writeTask = pool.submit(() -> {
      try {
        buffer.write(new byte[1 << 6], 0, 1 << 6);
      } catch (final InterruptedException e) {
        throw new AssertionError(e);
      }
    });
    final byte[] dest = new byte[SIZE];
    for (int i = 0; i < 2; i++) {
      int read = 0;
      while (read == 0) {
        read = buffer.poll(dest, 0, SIZE);
      }
      assertEquals(SIZE, read);
      Thread.sleep(100);
    }
    assertEquals(SIZE, buffer.poll(dest, 0, SIZE));
    Thread.sleep(100);
    assertTrue(writeTask.isCompletedNormally());
  }

  @Test
  public void testReadBlocking() throws InterruptedException {
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(1 << 5);
    final ForkJoinPool pool = new ForkJoinPool();
    final ForkJoinTask<?> readTask = pool.submit(() -> {
      try {
        buffer.read(new byte[SIZE * 3 + 1], 0, SIZE * 3 + 1);
      } catch (final InterruptedException e) {
        throw new AssertionError(e);
      }
    });
    final byte[] write = new byte[SIZE];
    for (int i = 0; i < 3; i++) {
      assertEquals(SIZE, buffer.offer(write, 0, SIZE));
      Thread.sleep(100);
      assertFalse(readTask.isDone());
    }
    assertEquals(SIZE, buffer.offer(write, 0, SIZE));
    Thread.sleep(100);
    assertTrue(readTask.isCompletedNormally());
  }

  private static final int MIN_READERS = 1;
  private static final int MIN_WRITERS = 1;
  private static final int MAX_WRITERS = 8;
  private static final int MAX_READERS = 8;
  private static final int CAPACITY = Long.BYTES * 8;
  private static final int[] UNALIGNED_SIZE_BOUNDS = {1, 4, Long.BYTES, 16, CAPACITY, CAPACITY * 2};
  private static final int[] ALIGNED_SIZE_BOUNDS = {Long.BYTES, Long.BYTES * 4, CAPACITY, CAPACITY * 4};
  private static final long BYTES_PER_UNALIGNED_TEST = CAPACITY * 128;
  private static final long BYTES_PER_PERFORMANCE_TEST = CAPACITY * 5_000;
  private static final long JOIN_TIMEOUT_MS = 15_000;

  public static List<Arguments> createParametersForUnalignedTest() {
    final List<Arguments> arguments = new ArrayList<>();
    for (int numReaders = MIN_READERS; numReaders <= MAX_READERS; numReaders++) {
      for (int numWriters = MIN_WRITERS; numWriters <= MAX_WRITERS; numWriters++) {
        for (int minReadSizeIndex = 0; minReadSizeIndex < UNALIGNED_SIZE_BOUNDS.length; minReadSizeIndex++) {
          for (int maxReadSizeIndex = minReadSizeIndex; maxReadSizeIndex < UNALIGNED_SIZE_BOUNDS.length; maxReadSizeIndex++) {
            for (int minWriteSizeIndex = 0; minWriteSizeIndex < UNALIGNED_SIZE_BOUNDS.length; minWriteSizeIndex++) {
              for (int maxWriteSizeIndex = minWriteSizeIndex; maxWriteSizeIndex < UNALIGNED_SIZE_BOUNDS.length; maxWriteSizeIndex++) {
                arguments.add(Arguments.arguments(numReaders, numWriters,
                    UNALIGNED_SIZE_BOUNDS[minReadSizeIndex], UNALIGNED_SIZE_BOUNDS[maxReadSizeIndex],
                    UNALIGNED_SIZE_BOUNDS[minWriteSizeIndex], UNALIGNED_SIZE_BOUNDS[maxWriteSizeIndex]));
              }
            }
          }
        }
      }
    }
    return arguments;
  }

  public static List<Arguments> createParametersForPerformanceTest() {
    final List<Arguments> arguments = new ArrayList<>();
    for (int numReaders = MIN_READERS; numReaders <= MAX_READERS; numReaders++) {
      for (int numWriters = MIN_WRITERS; numWriters <= MAX_WRITERS; numWriters++) {
        for (int minReadSizeIndex = 0; minReadSizeIndex < ALIGNED_SIZE_BOUNDS.length; minReadSizeIndex++) {
          for (int maxReadSizeIndex = minReadSizeIndex; maxReadSizeIndex < UNALIGNED_SIZE_BOUNDS.length; maxReadSizeIndex++) {
            for (int minWriteSizeIndex = 0; minWriteSizeIndex < ALIGNED_SIZE_BOUNDS.length; minWriteSizeIndex++) {
              for (int maxWriteSizeIndex = minWriteSizeIndex; maxWriteSizeIndex < ALIGNED_SIZE_BOUNDS.length; maxWriteSizeIndex++) {
                arguments.add(Arguments.arguments(numReaders, numWriters,
                    UNALIGNED_SIZE_BOUNDS[minReadSizeIndex], UNALIGNED_SIZE_BOUNDS[maxReadSizeIndex],
                    ALIGNED_SIZE_BOUNDS[minWriteSizeIndex], ALIGNED_SIZE_BOUNDS[maxWriteSizeIndex]));
              }
            }
          }
        }
      }
    }
    return arguments;
  }

  @MethodSource("createParametersForUnalignedTest")
  @ParameterizedTest
  public void multiReadersMultiWritersUnalignedTest(final int numReaders, final int numWriters, final int minReadSize,
      final int maxReadSize, final int minWriteSize, final int maxWriteSize) throws InterruptedException {
    multiReadersMultiWriterTestCore(numReaders, numWriters, minReadSize, maxReadSize, minWriteSize, maxWriteSize,
        BYTES_PER_UNALIGNED_TEST / numReaders, false);
  }

  @MethodSource("createParametersForPerformanceTest")
  @ParameterizedTest
  public void multiReadersMultiWritersPerformanceTest(final int numReaders, final int numWriters, final int minReadSize,
      final int maxReadSize, final int minWriteSize, final int maxWriteSize) throws InterruptedException {
    multiReadersMultiWriterTestCore(numReaders, numWriters, minReadSize, maxReadSize, minWriteSize, maxWriteSize,
        BYTES_PER_PERFORMANCE_TEST / numReaders, true);
  }

  private static void multiReadersMultiWriterTestCore(final int numReaders, final int numWriters,
      final int minReadSize, final int maxReadSize, final int minWriteSize, final int maxWriteSize, final long bytesPerReader,
      final boolean alignWrites) throws InterruptedException {
    final ConcurrentLinkedQueue<Throwable> throwables = new ConcurrentLinkedQueue<>();
    final AtomicSeedByteRingBuffer buffer = new AtomicSeedByteRingBuffer(CAPACITY);
    final AtomicLongArray bytesFinishedReading = new AtomicLongArray(numReaders);
    final List<Thread> readers = new ArrayList<>(numReaders);
    final List<Thread> writers = new ArrayList<>(numWriters);
    final CountDownLatch startLatch = new CountDownLatch(numReaders + numWriters);
    final ThreadGroup readersAndWriters = new ThreadGroup("readersAndWriters") {
      @Override public void uncaughtException(final Thread t, final Throwable e) {
        throwables.add(e);
      }
    };
    for (int i = 0; i < numReaders; i++) {
      final int threadNumber = i;
      readers.add(new Thread(readersAndWriters, () -> {
        final byte[] output = new byte[maxReadSize];
        startLatch.countDown();
        try {
          startLatch.await();
        } catch (final InterruptedException e) {
          throw new AssertionError(e);
        }
        int readSize;
        do {
          readSize = ThreadLocalRandom.current().nextInt(minReadSize, maxReadSize + 1);
          try {
            buffer.read(output, 0, readSize);
          } catch (final InterruptedException e) {
            return;
          } finally {
            buffer.checkInternalInvariants();
          }
          for (int j = 0; j < readSize; j++) {
            assertEquals(1, output[j]);
          }
        } while (bytesFinishedReading.addAndGet(threadNumber, readSize) < bytesPerReader);
      }, "Reader-" + i));
    }
    for (int i = 0; i < numWriters; i++) {
      writers.add(new Thread(readersAndWriters, () -> {
        final byte[] input = new byte[maxWriteSize];
        for (int j = 0; j < maxWriteSize; j++) {
          input[j] = 1;
        }
        startLatch.countDown();
        try {
          startLatch.await();
        } catch (final InterruptedException ignored) {
          // May happen spuriously if all readers finish before all writers start
          Thread.currentThread().interrupt();
        }
        while (true) {
          final int writeSize;
          if (alignWrites) {
            writeSize = ThreadLocalRandom.current().nextInt(minWriteSize >> 3, (maxWriteSize >> 3) + 1) << 3;
          } else {
            writeSize = ThreadLocalRandom.current().nextInt(minWriteSize, maxWriteSize + 1);
          }
          try {
            buffer.write(input, 0, writeSize);
          } catch (final InterruptedException e) {
            return;
          } finally {
            buffer.checkInternalInvariants();
          }
        }
      }, "Writer-" + i));
    }
    writers.forEach(Thread::start);
    readers.forEach(Thread::start);
    try {
      for (final Thread reader : readers) {
        reader.join(JOIN_TIMEOUT_MS);
        assertFalse(reader.isAlive(), () ->
            "A reader timed out before reading " + bytesPerReader + " bytes. "
              + "Bytes read per thread: " + bytesFinishedReading);
      }
    } finally {
      writers.forEach(Thread::interrupt);
      readers.forEach(Thread::interrupt);
    }
    for (final Thread writer : writers) {
      writer.join(JOIN_TIMEOUT_MS);
      assertFalse(writer.isAlive(), "A writer timed out after being interrupted");
    }
    if (!throwables.isEmpty()) {
      final AssertionError error = new AssertionError("Error in a reader or writer thread");
      throwables.forEach(error::addSuppressed);
      throw error;
    }
  }
}