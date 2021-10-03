package io.github.pr0methean.newbetterrandom.producer;

import io.github.pr0methean.newbetterrandom.buffer.AtomicSeedByteRingBuffer;
import java.security.SecureRandom;

public class SecureRandomSeedReader extends AbstractSeedReader {
  private final SecureRandom secureRandom;

  public SecureRandomSeedReader(AtomicSeedByteRingBuffer destBuffer, int sourceReadSize,
      final SecureRandom secureRandom) {
    super(destBuffer, sourceReadSize);
    this.secureRandom = secureRandom;
  }

  @Override protected void readToSourceBuffer() {
    System.arraycopy(secureRandom.generateSeed(sourceReadSize), 0, sourceBuffer, 0, sourceReadSize);
  }
}
