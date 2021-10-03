package io.github.pr0methean.newbetterrandom.consumer;

import io.github.pr0methean.newbetterrandom.buffer.AtomicSeedByteRingBuffer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class BasicEntropyCountingReplacingRandomGenerator extends BasicReplacingRandomGenerator
    implements EntropyCountingRandomGenerator {
  private final long entropyWhenFresh;
  private long entropy;
  public BasicEntropyCountingReplacingRandomGenerator(RandomGeneratorFactory delegateFactory,
      AtomicSeedByteRingBuffer seedSource, int seedSize) {
    super(delegateFactory, seedSource, seedSize);
    entropy = (long) seedSize * Byte.SIZE;
    entropyWhenFresh = entropy;
  }

  @Override public boolean nextBoolean() {
    entropy--;
    return super.nextBoolean();
  }

  @Override public void nextBytes(final byte[] bytes) {
    entropy -= ((long) bytes.length) * Byte.SIZE;
    super.nextBytes(bytes);
  }

  @Override public float nextFloat() {
    // TODO
    return super.nextFloat();
  }

  @Override public double nextDouble() {
    // TODO
    return super.nextDouble();
  }

  @Override public int nextInt() {
    entropy -= Integer.SIZE;
    return super.nextInt();
  }

  @Override public long nextLong() {
    entropy -= Long.SIZE;
    return super.nextLong();
  }

  @Override protected void setDelegate(RandomGenerator newDelegate) {
    super.setDelegate(newDelegate);
    entropy = entropyWhenFresh;
  }

  public long getEntropyBits() {
    return entropy;
  }
}
