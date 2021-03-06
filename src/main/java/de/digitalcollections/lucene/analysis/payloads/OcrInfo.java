package de.digitalcollections.lucene.analysis.payloads;

import com.google.common.math.IntMath;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class OcrInfo implements Comparable<OcrInfo> {

  private float horizontalOffset = -1.0f;
  private float verticalOffset = -1.0f;
  private float width = -1.0f;
  private float height = -1.0f;
  private int pageIndex = -1;
  private int lineIndex = -1;
  private int wordIndex = -1;

  private String term; // optional, only when returning search results

  OcrInfo() {
    // NOP
  }

  public OcrInfo(float horizontalOffset, float verticalOffset, float width, float height) {
    this(-1, horizontalOffset, verticalOffset, width, height);
  }

  public OcrInfo(int pageIndex, float horizontalOffset, float verticalOffset, float width, float height) {
    this.setHorizontalOffset(horizontalOffset);
    this.setVerticalOffset(verticalOffset);
    this.setWidth(width);
    this.setHeight(height);
    this.setPageIndex(pageIndex);
  }

  public OcrInfo(int pageIndex, int lineIndex, float horizontalOffset, float verticalOffset, float width, float height) {
    this(pageIndex, horizontalOffset, verticalOffset, width, height);
    this.lineIndex = lineIndex;
  }

  public OcrInfo(int pageIndex, int lineIndex, int wordIndex, float horizontalOffset, float verticalOffset, float width, float height) {
    this(pageIndex, lineIndex, horizontalOffset, verticalOffset, width, height);
    this.wordIndex = wordIndex;
  }

  /**
   * Parse an {@link OcrInfo} object from a character buffer.
   *
   * The string contains concatenated pairs of single-character keys and numerical values, e.g. `x1337`.
   * Valid keys are:
   * - **p**: Page index, ranging from 0 to 2^pageBits (optional)
   * - **l**: Line index, ranging from 0 to 2^lineBits (optional)
   * - **n**: Word index, ranging from 0 to 2^wordBits (optional)
   * - **x**: Horizontal offset as floating point value in range [0...1] without leading '0.' (mandatory)
   * - **y**: Vertical offset as floating point value in range [0...1] without leading '0.' (mandatory)
   * - **w**: Width as floating point value in range [0...1] without leading '0.' (mandatory)
   * - **h**: Height as floating point value in range [0...1] without leading '0.' (mandatory)
   *
   * Here es an example:
   *
   * **Payload string:** `p27l50n13x131y527w879h053
   *
   * **Decoding:**
   *
   * ```
   *  step    |  pageIndex | lineIndex  | wordIndex |   x   |   y   |   w   |  h
   * ---------|------------|------------|-----------|-------|-------|-------|-------
   * Split    |      27    |       50   |      13   |   131 |   527 |   879 |    53
   * Decoded  |      27    |       50   |      13   | 0.131 | 0.527 | 0.879 | 0.053
   * ```
   *
   * @param buffer Input character buffer
   * @param offset Offset of the encoded character information
   * @param length Length of the encoded character information
   * @param wordBits Number of bits used for encoding the word index
   * @param lineBits Number of bits used for encoding the line index
   * @param pageBits Number of bits used for encoding the page index
   * @return The decoded {@link OcrInfo} instance
   */
  public static OcrInfo parse(char[] buffer, int offset, int length, int wordBits, int lineBits, int pageBits) {
    OcrInfo info = new OcrInfo();

    String payload = new String(buffer, offset, length).toLowerCase();
    String[] payloadParts = payload.split("(?=\\D)");
    Set<Character> seenKeys = new HashSet<>();

    for (String payloadPart : payloadParts) {
      char key = payloadPart.charAt(0);
      if (seenKeys.contains(key)) {
        throw new IllegalArgumentException(String.format("Invalid payload %s: duplicate key '%c'", payload, key));
      } else {
        seenKeys.add(key);
      }
      String value = payloadPart.substring(1);
      switch (key) {
        case 'p':
          info.setPageIndex(parseIndex(value, pageBits)); break;
        case 'l':
          info.setLineIndex(parseIndex(value, lineBits)); break;
        case 'n':
          info.setWordIndex(parseIndex(value, wordBits)); break;
        case 'x':
          info.setHorizontalOffset(Float.parseFloat("0." + value)); break;
        case 'y':
          info.setVerticalOffset(Float.parseFloat("0." + value)); break;
        case 'w':
          info.setWidth(Float.parseFloat("0." + value)); break;
        case 'h':
          info.setHeight(Float.parseFloat("0." + value)); break;
        default:
          throw new IllegalArgumentException(String.format(
              "Could not parse OCR bounding box information, string was %s, invalid character was %c",
              new String(buffer, offset, length), key));
      }
    }
    if (info.getHorizontalOffset() < 0 || info.getHorizontalOffset() < 0 || info.getWidth() < 0 || info.getHeight() < 0) {
      throw new IllegalArgumentException(String.format(
          "One or more coordinates are missing from payload (was %s), make sure you have 'x', 'y', 'w' and 'h' set!",
          payload));
    }
    if (pageBits > 0 && info.getPageIndex() < 0) {
      throw new IllegalArgumentException(String.format(
          "Page index is missing from payload (was: '%s'), fix payload or set the 'pageBits' option to 0.", payload));
    }
    if (lineBits > 0 && info.getLineIndex() < 0) {
      throw new IllegalArgumentException(String.format(
          "Line index is missing from payload (was: '%s'), fix payload or set the 'lineBits' option to 0.", payload));
    }
    if (wordBits > 0 && info.getWordIndex() < 0) {
      throw new IllegalArgumentException(String.format(
          "Word index is missing from payload (was: '%s'), fix payload or set the 'wordBits' option to 0.", payload));
    }
    return info;
  }

  private static int parseIndex(String value, int numBits) {
    int index = Integer.parseInt(value);
    if (index >= IntMath.pow(2, numBits)) {
      throw new IllegalArgumentException(String.format("Value %d needs more than %d bits (valid values range from 0 to %d).",
                                                       index, numBits, IntMath.pow(2, numBits) - 1));
    }
    return index;
  }

  public float getHorizontalOffset() {
    return horizontalOffset;
  }

  public void setHorizontalOffset(float horizontalOffset) {
    this.horizontalOffset = horizontalOffset;
  }

  private void checkCoordinate(float coordinate) {
    if (coordinate > 1) {
      throw new IllegalArgumentException(String.format("Coordinates can at most be 1.0, was %1f!", coordinate));
    }
  }

  public float getVerticalOffset() {
    return verticalOffset;
  }

  public void setVerticalOffset(float verticalOffset) {
    checkCoordinate(verticalOffset);
    this.verticalOffset = verticalOffset;
  }

  public float getWidth() {
    return width;
  }

  public void setWidth(float width) {
    checkCoordinate(width);
    this.width = width;
  }

  public float getHeight() {
    return height;
  }

  public void setHeight(float height) {
    checkCoordinate(height);
    this.height = height;
  }

  public int getPageIndex() {
    return pageIndex;
  }

  public void setPageIndex(int pageIndex) {
    this.pageIndex = pageIndex;
  }

  public String getTerm() {
    return term;
  }

  public void setTerm(String term) {
    this.term = term;
  }

  public int getLineIndex() {
    return lineIndex;
  }

  public void setLineIndex(int lineIndex) {
    this.lineIndex = lineIndex;
  }

  public int getWordIndex() {
    return wordIndex;
  }

  public void setWordIndex(int wordIndex) {
    this.wordIndex = wordIndex;
  }

  @Override
  public String toString() {
    return "OcrInfo{"
        + "horizontalOffset=" + horizontalOffset
        + ", verticalOffset=" + verticalOffset
        + ", width=" + width
        + ", height=" + height
        + ", pageIndex=" + pageIndex
        + ", lineIndex=" + lineIndex
        + ", wordIndex=" + wordIndex
        + ", term='" + term + '\''
        + '}';
  }

  @Override
  public int compareTo(OcrInfo other) {
    return Comparator
        .comparing(OcrInfo::getPageIndex)
        .thenComparing(OcrInfo::getLineIndex)
        .thenComparing(OcrInfo::getWordIndex)
        .thenComparing(OcrInfo::getHorizontalOffset)
        .thenComparing(OcrInfo::getVerticalOffset)
        .compare(this, other);
  }
}
