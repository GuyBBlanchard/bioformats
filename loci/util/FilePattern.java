//
// FilePattern.java
//

package loci.util;

import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Vector;

/**
 * FilePattern is a collection of methods for handling VisBio file patterns.
 *
 * Examples:
 * <ul>
 *   <li>C:\data\BillM\sdub&lt;1-12&gt;.pic</li>
 *   <li>C:\data\Kevin\80&lt;01-59&gt;0&lt;2-3&gt;.pic</li>
 *   <li>/data/Josiah/cell-Z&lt;0-39&gt;0.C&lt;0-1&gt;.tiff</li>
 * </ul>
 */
public class FilePattern {

  // -- Fields --

  /** The file pattern string. */
  private String pattern;

  /** The validity of the file pattern. */
  private boolean valid;

  /** Error message generated during file pattern construction. */
  private String msg;

  /** Indices into the pattern indicating the start of a numerical block. */
  private int[] startIndex;

  /** Indices into the pattern indicating the end of a numerical block. */
  private int[] endIndex;

  /** First number of each numerical block. */
  private BigInteger[] begin;

  /** Last number of each numerical block. */
  private BigInteger[] end;

  /** Step size of each numerical block. */
  private BigInteger[] step;

  /** Total numbers withins each numerical block. */
  private int[] count;

  /** Whether each numerical block is fixed width. */
  private boolean[] fixed;

  /** File listing for this file pattern. */
  private String[] files;

  /** Hardcoded directory to avoid symbolic link problem. */
  private static String directory; // YTW


  // -- Constructors --

  /** Creates a pattern object using the given file as a template. */
  public FilePattern(File file) { this(file,"."); }

  public FilePattern(File file, String directory) {
    FilePattern.directory = directory;
    FilePattern.findPattern(file);
  }

  /** Creates a pattern object for files with the given file pattern string. */
  public FilePattern(String pattern) {
    this.pattern = pattern;
    valid = false;
    if (pattern == null) {
      msg = "Null pattern string.";
      return;
    }

    // locate numerical blocks
    int len = pattern.length();
    Vector lt = new Vector(len);
    Vector gt = new Vector(len);
    int left = -1;
    while (true) {
      left = pattern.indexOf("<", left + 1);
      if (left < 0) break;
      lt.add(new Integer(left));
    }
    int right = -1;
    while (true) {
      right = pattern.indexOf(">", right + 1);
      if (right < 0) break;
      gt.add(new Integer(right));
    }

    // assemble numerical block indices
    int num = lt.size();
    if (num != gt.size()) {
      msg = "Mismatched numerical block markers.";
      return;
    }
    startIndex = new int[num];
    endIndex = new int[num];
    for (int i=0; i<num; i++) {
      int val = ((Integer) lt.elementAt(i)).intValue();
      if (i > 0 && val < endIndex[i - 1]) {
        msg = "Bad numerical block marker order.";
        return;
      }
      startIndex[i] = val;
      val = ((Integer) gt.elementAt(i)).intValue();
      if (val <= startIndex[i]) {
        msg = "Bad numerical block marker order.";
        return;
      }
      endIndex[i] = val + 1;
    }

    // parse numerical blocks
    begin = new BigInteger[num];
    end = new BigInteger[num];
    step = new BigInteger[num];
    count = new int[num];
    fixed = new boolean[num];
    for (int i=0; i<num; i++) {
      String block = pattern.substring(startIndex[i], endIndex[i]);
      int dash = block.indexOf("-");
      String b, e, s;
      if (dash < 0) {
        // no range; assume entire block is a single number (e.g., <15>)
        b = e = block.substring(1, block.length() - 1);
        s = "1";
      }
      else {
        int colon = block.indexOf(":");
        b = block.substring(1, dash);
        if (colon < 0) {
          e = block.substring(dash + 1, block.length() - 1);
          s = "1";
        }
        else {
          e = block.substring(dash + 1, colon);
          s = block.substring(colon + 1, block.length() - 1);
        }
      }
      try {
        begin[i] = new BigInteger(b);
        end[i] = new BigInteger(e);
        if (begin[i].compareTo(end[i]) > 0) {
          msg = "Begin value cannot be greater than ending value.";
          return;
        }
        step[i] = new BigInteger(s);
        if (step[i].compareTo(BigInteger.ONE) < 0) {
          msg = "Step value must be at least one.";
          return;
        }
        count[i] = end[i].subtract(begin[i]).divide(step[i]).intValue() + 1;
        fixed[i] = b.length() == e.length();
      }
      catch (NumberFormatException exc) {
        msg = "Invalid numerical range values.";
        return;
      }
    }

    // build file listing
    Vector v = new Vector();
    buildFiles("", num, v);
    files = new String[v.size()];
    v.copyInto(files);

    valid = true;
  }


  // -- FilePattern API methods --

  /** Gets whether the file pattern string is valid. */
  public boolean isValid() { return valid; }

  /** Gets the file pattern error message, if any. */
  public String getErrorMessage() { return msg; }

  /** Gets the first number of each numerical block. */
  public BigInteger[] getFirst() { return begin; }

  /** Gets the last number of each numerical block. */
  public BigInteger[] getLast() { return end; }

  /** Gets the step increment of each numerical block. */
  public BigInteger[] getStep() { return step; }

  /** Gets the total count of each numerical block. */
  public int[] getCount() { return count; }

  /** Gets a listing of all files matching the given file pattern. */
  public String[] getFiles() { return files; }

  /** Gets the pattern's text string before any numerical ranges. */
  public String getPrefix() {
    int s = pattern.lastIndexOf(File.separator) + 1;
    int e;
    if (startIndex.length > 0) e = startIndex[0];
    else {
      int dot = pattern.lastIndexOf(".");
      e = dot < s ? pattern.length() : dot;
    }
    return s <= e ? pattern.substring(s, e) : "";
  }

  /** Gets the pattern's text string before the given numerical block. */
  public String getPrefix(int i) {
    if (i < 0 || i >= startIndex.length) return null;
    int s = i > 0 ? endIndex[i - 1] :
      (pattern.lastIndexOf(File.separator) + 1);
    int e = startIndex[i];
    return s <= e ? pattern.substring(s, e) : null;
  }

  /** Gets the pattern's text string before each numerical block. */
  public String[] getPrefixes() {
    String[] s = new String[startIndex.length];
    for (int i=0; i<s.length; i++) s[i] = getPrefix(i);
    return s;
  }


  // -- Utility methods --


  // YTW
  public static String findPattern(File file, String dir) {
    directory = dir;
    return findPattern(file);
  }

  /** Identifies the group pattern from a given file within that group. */
  public static String findPattern(File file) {
    if (directory==null) directory = "."; // YTW
    //file = file.getAbsoluteFile();
    //File dir = file.getParentFile();
    //if (dir == null) return null;
    //String dirname = dir.getAbsolutePath();
    //if (!dirname.endsWith(File.separator)) dirname += File.separator;
    //String name = file.getName();

    // YTW: no getParentFile() to avoid symbolic link problem
    File dir = new File(directory);
    if (dir == null) return null;
    String dirname = dir.getAbsolutePath();
    if (!dirname.endsWith(File.separator)) dirname += File.separator;
    String name = file.getName();

    // compile list of numerical blocks
    int len = name.length();
    int bound = (len + 1) / 2; // max possible number of pairs of indices?
    int[] indexList = new int[bound];
    int[] endList = new int[bound];
    int q = 0;
    boolean num = false;
    int ndx = -1, e = 0;
    for (int i=0; i<len; i++) {
      char c = name.charAt(i);
      if (c >= '0' && c <= '9') {
        if (num) e++;
        else {
          num = true;
          ndx = i;
          e = ndx + 1;
        }
      }
      else if (num) {
        num = false;
        indexList[q] = ndx;
        endList[q] = e;
        q++;
      }
    }
    if (num) {
      indexList[q] = ndx;
      endList[q] = e;
      q++;
    }

    // analyze each block, building pattern as we go
    StringBuffer sb = new StringBuffer(dirname);

    for (int i=0; i<q; i++) {
      int last = i > 0 ? endList[i - 1] : 0;
      sb.append(name.substring(last, indexList[i]));
      String pre = name.substring(0, indexList[i]);
      String post = name.substring(endList[i]);

      NumberFilter filter = new NumberFilter(pre, post);
      File[] list = dir.listFiles(filter);
      if (list == null || list.length == 0) return null;
      if (list.length == 1) {
        // false alarm; this number block is constant
        sb.append(name.substring(indexList[i], endList[i]));
        continue;
      }
      boolean fix = true;
      for (int j=0; j<list.length; j++) {
        if (list[j].getName().length() != len) {
          fix = false;
          break;
        }
      }
      if (fix) {
        // tricky; this fixed-width block could represent multiple numberings
        int width = endList[i] - indexList[i];

        // check each character for duplicates
        boolean[] same = new boolean[width];
        for (int j=0; j<width; j++) {
          same[j] = true;
          int jx = indexList[i] + j;
          char c = name.charAt(jx);
          for (int k=0; k<list.length; k++) {
            if (list[k].getName().charAt(jx) != c) {
              same[j] = false;
              break;
            }
          }
        }

        // break down each sub-block
        int j = 0;
        while (j < width) {
          int jx = indexList[i] + j;
          if (same[j]) {
            sb.append(name.charAt(jx));
            j++;
          }
          else {
            while (j < width && !same[j]) j++;
            String p = findPattern(name, dir, jx, indexList[i] + j, "");
            if (p == null) {
              // unable to find an appropriate breakdown of numerical blocks
              return null;
            }
            sb.append(p);
          }
        }
      }
      else {
        // assume variable-width block represents only one numbering
        BigInteger[] numbers = new BigInteger[list.length];
        for (int j=0; j<list.length; j++) {
          numbers[j] = filter.getNumber(list[j].getName());
        }
        Arrays.sort(numbers);
        String bounds = getBounds(numbers, false);
        if (bounds == null) return null;
        sb.append(bounds);
      }
    }
    sb.append(q > 0 ? name.substring(endList[q - 1]) : name);
    return sb.toString();
  }


  // -- Utility helper methods --

  /** Recursive method for parsing a fixed-width numerical block. */
  private static String findPattern(String name,
    File dir, int ndx, int end, String p)
  {
    if (ndx == end) return p;
    for (int i=end-ndx; i>=1; i--) {
      NumberFilter filter = new NumberFilter(
        name.substring(0, ndx), name.substring(ndx + i));
      File[] list = dir.listFiles(filter);
      BigInteger[] numbers = new BigInteger[list.length];
      for (int j=0; j<list.length; j++) {
        numbers[j] = new BigInteger(list[j].getName().substring(ndx, ndx + i));
      }
      Arrays.sort(numbers);
      String bounds = getBounds(numbers, true);
      if (bounds == null) continue;
      String pat = findPattern(name, dir, ndx + i, end, p + bounds);
      if (pat != null) return pat;
    }
    // no combination worked; this parse path is infeasible
    return null;
  }

  /**
   * Gets a string containing start, end and step values
   * for a sorted list of numbers.
   */
  private static String getBounds(BigInteger[] numbers, boolean fixed) {
    if (numbers.length < 2) return null;
    BigInteger b = numbers[0];
    BigInteger e = numbers[numbers.length - 1];
    BigInteger s = numbers[1].subtract(b);
    if (s.equals(BigInteger.ZERO)) {
      // step size must be positive
      return null;
    }
    for (int i=2; i<numbers.length; i++) {
      if (!numbers[i].subtract(numbers[i - 1]).equals(s)) {
        // step size is not constant
        return null;
      }
    }
    String sb = b.toString();
    String se = e.toString();
    StringBuffer bounds = new StringBuffer("<");
    if (fixed) {
      int zeroes = se.length() - sb.length();
      for (int i=0; i<zeroes; i++) bounds.append("0");
    }
    bounds.append(sb);
    bounds.append("-");
    bounds.append(se);
    if (!s.equals(BigInteger.ONE)) {
      bounds.append(":");
      bounds.append(s);
    }
    bounds.append(">");
    return bounds.toString();
  }


  // -- Helper methods --

  /** Recursive method for building filenames for the file listing. */
  private void buildFiles(String prefix, int ndx, Vector files) {
    // compute bounds for constant (non-block) pattern fragment
    int num = startIndex.length;
    int n1 = ndx == 0 ? 0 : endIndex[ndx - 1];
    int n2 = ndx == num ? pattern.length() : startIndex[ndx];
    String pre = pattern.substring(n1, n2);

    if (ndx == 0) files.add(pre + prefix);
    else {
      // for (int i=begin[ndx]; i<end[ndx]; i+=step[ndx])
      BigInteger bi = begin[--ndx];
      while (bi.compareTo(end[ndx]) <= 0) {
        String s = bi.toString();
        if (fixed[ndx]) {
          int zeroes = end[ndx].toString().length() - s.length();
          for (int j=0; j<zeroes; j++) s = "0" + s;
        }
        buildFiles(s + pre + prefix, ndx, files);
        bi = bi.add(step[ndx]);
      }
    }
  }


  // -- Main method --

  /** Method for testing file pattern logic. */
  public static void main(String[] args) {
    File file = args.length < 1 ?
      new File(System.getProperty("user.dir")).listFiles()[0] :
      new File(args[0]);
    System.out.println("File: " + file.getAbsoluteFile());
    String pat = findPattern(file);
    if (pat == null) System.out.println("No pattern found.");
    else {
      System.out.println("Pattern = " + pat);
      FilePattern fp = new FilePattern(pat);
      if (fp.isValid()) {
        System.out.println("Pattern is valid.");
        System.out.println("Files:");
        String[] ids = fp.getFiles();
        for (int i=0; i<ids.length; i++) {
          System.out.println("  #" + i + ": " + ids[i]);
        }
      }
      else System.out.println("Pattern is invalid: " + fp.getErrorMessage());
    }
  }

}


// -- Notes --

// Some patterns observed:
//
//   TAABA1.PIC TAABA2.PIC TAABA3.PIC ... TAABA45.PIC
//
//   0m.tiff 3m.tiff 6m.tiff ... 36m.tiff
//
//   cell-Z0.C0.tiff cell-Z1.C0.tiff cell-Z2.C0.tiff ... cell-Z39.C0.tiff
//   cell-Z0.C1.tiff cell-Z1.C1.tiff cell-Z2.C1.tiff ... cell-Z39.C1.tiff
//
//   CRG401.PIC
//
//   TST00101.PIC TST00201.PIC TST00301.PIC
//   TST00102.PIC TST00202.PIC TST00302.PIC
//
//   800102.pic 800202.pic 800302.pic ... 805902.pic
//   800103.pic 800203.pic 800303.pic ... 805903.pic
//
//   nd400102.pic nd400202.pic nd400302.pic ... nd406002.pic
//   nd400103.pic nd400203.pic nd400303.pic ... nd406003.pic
//
//   WTERZ2_Series13_z000_ch00.tif ... WTERZ2_Series13_z018_ch00.tif
//
// --------------------------------------------------------------------------
//
// The file pattern notation defined here encompasses all patterns above.
//
//   TAABA<1-45>.PIC
//   <0-36:3>m.tiff
//   cell-Z<0-39>.C<0-1>.tiff
//   CRG401.PIC
//   TST00<1-3>0<1-2>.PIC
//   80<01-59>0<2-3>.pic
//   nd40<01-60>0<2-3>.pic
//   WTERZ2_Series13_z0<00-18>_ch00.tif
//
// In general: <B-E:S> where B is the start number, E is the end number, and S
// is the step increment. If zero padding has been used, the start number B
// will have leading zeroes to indicate that. If the step increment is one, it
// can be omitted.
//
// --------------------------------------------------------------------------
//
// If file groups not limited to numbering need to be handled, we can extend
// the notation as follows:
//
// A pattern such as:
//
//   ABCR.PIC ABCG.PIC ABCB.PIC
//
// Could be represented as:
//
//   ABC<R|G|B>.PIC
//
// If such cases come up, they will need to be identified heuristically and
// incorporated into the detection algorithm.
//
// --------------------------------------------------------------------------
//
// Here is a sketch of the algorithm for determining the pattern from a given
// file within a particular group:
//
//   01 - Detect number blocks within the file name, marking them with stars.
//        For example:
//
//          xyz800303b.pic -> xyz<>b.pic
//
//        Where <> represents a numerical block with unknown properties.
//
//   02 - Get a file listing for all files matching the given pattern. In the
//        example above, we'd get:
//
//        xyz800102b.pic, xyz800202b.pic, ..., xyz805902b.pic,
//        xyz800103b.pic, xyz800203b.pic, ..., xyz805903b.pic
//
//   03 - There are two possibilities: "fixed width" and "variable width."
//
//        Variable width: Not all filenames are the same length in characters.
//        Assume the block only covers a single number. Extract that number
//        from each filename, sort them and analyze as described below.
//
//        Fixed width: All filenames are the same length in characters. The
//        block could represent more than one number.
//
//        First, for each character, determine if that character varies between
//        filenames. If not, lock it down, splitting the block as necessary
//        into fixed-width blocks. When finished, the above example looks like:
//
//          xyz80<2>0<1>b.pic
//
//        Where <N> represents a numerical block of width N.
//
//        For each remaining block, extract the numbers from each matching
//        filename, sort the lists, and analyze as described below.
//
//   04 - In either case, analyze each list of numbers. The first on the list
//        is B. The last one is E. And S is the second one minus B. But check
//        the list to make sure no numbers are missing for that step size.
//
// NOTE: The fixed width algorithm above is insufficient for patterns like
// "0101.pic" through "2531.pic," where no fixed constant pads the two
// numerical counts. An additional step is required, as follows:
//
//   05 - For each fixed-width block, recursively divide it into pieces, and
//        analyze the numerical scheme according to those pieces. For example,
//        in the problem case given above, we'd have:
//
//          <4>.pic
//
//        Recursively, we'd analyze:
//
//          <4>.pic
//          <3><R1>.pic
//          <2><R2>.pic
//          <1><R3>.pic
//
//        The <Rx> blocks represent recursive calls to analyze the remainder of
//        the width.
//
//        The function decides if a given combination of widths is valid by
//        determining if each individual width is valid. An individual width
//        is valid if the computed B, S and E properly cover the numerical set.
//
//        If no combination of widths is found to be valid, the file numbering
//        is screwy. Print an error message.
