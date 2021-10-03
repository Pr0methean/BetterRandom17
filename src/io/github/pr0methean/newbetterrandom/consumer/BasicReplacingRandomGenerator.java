package io.github.pr0methean.newbetterrandom.consumer;

import io.github.pr0methean.newbetterrandom.buffer.AtomicSeedByteRingBuffer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class BasicReplacingRandomGenerator<T extends RandomGenerator>
    extends AbstractReplacingRandomGenerator<T> {
  private T delegate;

  public BasicReplacingRandomGenerator(RandomGeneratorFactory<T> delegateFactory,
      AtomicSeedByteRingBuffer seedSource, int seedSize) {
    super(delegateFactory, seedSource, seedSize);
  }

  @Override protected T getDelegate() {
    return delegate;
  }

  @Override protected void setDelegate(T newDelegate) {
    delegate = newDelegate;
  }
}
