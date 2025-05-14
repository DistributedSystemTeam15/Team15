package cm.lock;

import java.util.Objects;

/** 개행(\\n) 기준 논리적 줄 구간 잠금을 표현 */
public final class LineRange {
    private final int start;      // inclusive
    private final int end;        // inclusive
    private final String owner;   // 사용자 ID

    public LineRange(int start, int end, String owner) {
        if (start > end) { int t = start; start = end; end = t; }
        this.start = start;
        this.end   = end;
        this.owner = owner;
    }
    /* 기본 getters */
    public int start()  { return start; }
    public int end()    { return end; }
    public String owner(){ return owner; }

    /* 두 구간이 겹치는가? */
    public boolean overlaps(LineRange other) {
        return this.end >= other.start && other.end >= this.start;
    }

    /* equals / hashCode → start·end·owner 모두 동일해야 같은 잠금 */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineRange lr)) return false;
        return start==lr.start && end==lr.end && Objects.equals(owner, lr.owner);
    }
    @Override public int hashCode() {
        return Objects.hash(start, end, owner);
    }
}
