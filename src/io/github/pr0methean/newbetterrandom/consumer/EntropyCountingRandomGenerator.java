package io.github.pr0methean.newbetterrandom.consumer;

import java.util.random.RandomGenerator;

public interface EntropyCountingRandomGenerator extends RandomGenerator {
  public long getEntropyBits();
}
