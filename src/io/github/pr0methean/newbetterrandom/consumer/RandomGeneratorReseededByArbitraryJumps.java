package io.github.pr0methean.newbetterrandom.consumer;

import io.github.pr0methean.newbetterrandom.buffer.AtomicSeedByteRingBuffer;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

public class RandomGeneratorReseededByArbitraryJumps implements RandomGenerator.SplittableGenerator {
  private final ArbitrarilyJumpableGenerator delegate;
  private final AtomicSeedByteRingBuffer seedSource;
  private final int seedSize;
  private final byte[] seedBuffer;

  public RandomGeneratorReseededByArbitraryJumps(final ArbitrarilyJumpableGenerator delegate,
      final AtomicSeedByteRingBuffer seedSource, final int seedSize) {
    this.delegate = delegate;
    this.seedSource = seedSource;
    this.seedSize = seedSize;
    seedBuffer = new byte[seedSize];
  }

  @Override public long nextLong() {
    if (seedSource.pollAllOrNone(seedBuffer, 0, seedSize)) {
      for (int i = 0; i < seedSize * Byte.SIZE; i++) {
        final int index = i / Byte.SIZE;
        final int offset = (i - Byte.SIZE) % Byte.SIZE;
        if (((seedBuffer[index] >> offset) & 1) != 0) {
          delegate.jumpPowerOfTwo(i);
        }
      }
    }
    return delegate.nextLong();
  }

  @Override public SplittableGenerator split() {
    return null; // TODO
  }

  @Override public SplittableGenerator split(SplittableGenerator splittableGenerator) {
    return null;
  }

  @Override public Stream<SplittableGenerator> splits(long l) {
    return null;
  }

  @Override public Stream<SplittableGenerator> splits(SplittableGenerator splittableGenerator) {
    return null;
  }

  @Override
  public Stream<SplittableGenerator> splits(long l, SplittableGenerator splittableGenerator) {
    return null;
  }
}
