package io.github.pr0methean.newbetterrandom.consumer;

import io.github.pr0methean.newbetterrandom.buffer.AtomicSeedByteRingBuffer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public abstract class AbstractReplacingRandomGenerator<T extends RandomGenerator>
    implements RandomGenerator {
  protected final RandomGeneratorFactory<T> delegateFactory;
  protected final AtomicSeedByteRingBuffer seedSource;
  protected final int seedSize;
  private final byte[] seedBuffer;

  public AbstractReplacingRandomGenerator(final RandomGeneratorFactory<T> delegateFactory,
      final AtomicSeedByteRingBuffer seedSource, final int seedSize) {
    this.delegateFactory = delegateFactory;
    this.seedSource = seedSource;
    this.seedSize = seedSize;
    seedBuffer = new byte[seedSize];
  }

  protected abstract T getDelegate();

  protected abstract void setDelegate(T newDelegate);

  @Override public long nextLong() {
    if (getDelegate() == null) {
      try {
        seedSource.read(seedBuffer, 0, seedSize);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      setDelegate(delegateFactory.create(seedBuffer));
    } else if (seedSource.pollAllOrNone(seedBuffer, 0, seedSize)) {
      setDelegate(delegateFactory.create(seedBuffer));
    }
    return getDelegate().nextLong();
  }
}
