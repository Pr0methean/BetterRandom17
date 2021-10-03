package io.github.pr0methean.newbetterrandom.consumer;

import io.github.pr0methean.newbetterrandom.buffer.AtomicSeedByteRingBuffer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Stream;

public class ThreadLocalReplacingRandomGenerator<T extends RandomGenerator>
    extends AbstractReplacingRandomGenerator<T> implements RandomGenerator.SplittableGenerator {
  private final ThreadLocal<T> delegateThreadLocal;

  public ThreadLocalReplacingRandomGenerator(RandomGeneratorFactory<T> delegateFactory,
      AtomicSeedByteRingBuffer seedSource, int seedSize) {
    this(delegateFactory, seedSource, seedSize, ThreadLocal.withInitial(() -> {
      byte[] seed = new byte[seedSize];
      try {
        seedSource.read(seed, 0, seedSize);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      return delegateFactory.create(seed);
    }));
  }

  public ThreadLocalReplacingRandomGenerator(RandomGeneratorFactory<T> delegateFactory,
      AtomicSeedByteRingBuffer seedSource, int seedSize, ThreadLocal<T> delegateThreadLocal) {
    super(delegateFactory, seedSource, seedSize);
    this.delegateThreadLocal = delegateThreadLocal;
  }

  @Override protected T getDelegate() {
    return delegateThreadLocal.get();
  }

  @Override protected void setDelegate(T newDelegate) {
    delegateThreadLocal.set(newDelegate);
  }

  // Since delegate is thread-local, we can effectively split without actually splitting.
  @Override public SplittableGenerator split() {
    return this;
  }

  @Override public SplittableGenerator split(SplittableGenerator splittableGenerator) {
    final int seedSize = this.seedSize;
    final ThreadLocal<T> threadLocalWithSplittableSeedSource = ThreadLocal.withInitial(
        () -> {
          byte[] delegateSeed = new byte[seedSize];
          splittableGenerator.nextBytes(delegateSeed);
          return delegateFactory.create(delegateSeed);
        });
    return new ThreadLocalReplacingRandomGenerator<T>(delegateFactory, seedSource, seedSize,
        threadLocalWithSplittableSeedSource);
  }

  @Override public Stream<SplittableGenerator> splits(long l) {
    return Stream.generate(() -> this);
  }

  @Override public Stream<SplittableGenerator> splits(SplittableGenerator splittableGenerator) {
    return Stream.generate(() -> split(splittableGenerator.split()));
  }

  @Override
  public Stream<SplittableGenerator> splits(long l, SplittableGenerator splittableGenerator) {
    return splits(splittableGenerator);
  }
}
