package liu.poe.fiveway.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 簡易語意化版本比較 (主.次.修[-預release])，對應原版 semver.valid / semver.gt */
public record Version(int major, int minor, int patch, String pre) implements Comparable<Version> {

    private static final Pattern P =
            Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+.*)?$");

    /** 無法解析時回傳 null */
    public static Version parse(String s) {
        if (s == null) return null;
        Matcher m = P.matcher(s.trim());
        if (!m.matches()) return null;
        return new Version(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group(4));
    }

    @Override
    public int compareTo(Version o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) return c;
        c = Integer.compare(minor, o.minor);
        if (c != 0) return c;
        c = Integer.compare(patch, o.patch);
        if (c != 0) return c;
        // 有預release標記者小於正式版
        if (pre == null && o.pre == null) return 0;
        if (pre == null) return 1;
        if (o.pre == null) return -1;
        return pre.compareTo(o.pre);
    }
}
