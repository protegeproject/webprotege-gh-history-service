package edu.stanford.protege.github.cloneservice.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Object-oriented refactor of the original FastOboDiff utilities.
 * <p>
 * Responsibilities are separated into dedicated collaborators:
 * <ul>
 *   <li>{@link FastOboDiff} – a façade that wires collaborators and exposes a simple API.</li>
 *   <li>{@link OboParser} – parses OBO text into an {@link OboDoc} model.</li>
 *   <li>{@link OboDiffer} – computes a {@link DiffResult} between two {@link OboDoc}s.</li>
 *   <li>{@link OboRenderer} – renders BEFORE/AFTER views from a {@link DiffResult}.</li>
 *   <li>{@link OboIO} – convenience I/O helpers (UTF‑8 read/write).</li>
 * </ul>
 * All immutable value types remain as records.
 */
public final class FastOboDiff {

    private final DiffOptions options;

    private final OboParser parser;

    private final OboDiffer differ;

    private final OboRenderer renderer;

    /**
     * Create a FastOboDiff with custom {@link DiffOptions}.
     */
    public FastOboDiff(DiffOptions options) {
        this.options = Objects.requireNonNull(options);
        this.parser = new OboParser();
        this.differ = new OboDiffer(this.options);
        this.renderer = new OboRenderer();
    }

    /**
     * High-level diff from two OBO texts using this instance's options.
     */
    public DiffResult diff(String beforeText, String afterText) {
        return differ.diff(parser.parse(beforeText), parser.parse(afterText));
    }

    /**
     * Render BEFORE file: old front matter + removed + changed(old).
     */
    public String renderBefore(DiffResult r) {
        return renderer.renderBefore(r);
    }

    /**
     * Render AFTER file: new front matter + added + changed(new).
     */
    public String renderAfter(DiffResult r) {
        return renderer.renderAfter(r);
    }

    /**
     * Parses OBO text into a model.
     */
    static final class OboParser {

        OboDoc parse(String content) {
            var lf = Text.normalizeEol(content);
            var lines = Arrays.asList(lf.split("\n" , -1));
            var n = lines.size();

            // Front matter
            var i = 0;
            var front = new StringBuilder();
            while(i < n && !isHeaderLine(lines.get(i))) {
                front.append(lines.get(i)).append('\n');
                i++;
            }
            var frontMatter = Text.stripTrailingBlankLines(front.toString());

            // Stanzas
            List<Stanza> stanzas = new ArrayList<>();
            while(i < n) {
                if(isHeaderLine(lines.get(i))) {
                    var type = extractType(lines.get(i));
                    var start = i;
                    var j = i + 1;
                    while(j < n && !isHeaderLine(lines.get(j))) j++;
                    var end = j - 1;
                    while(end > start && lines.get(end).trim().isEmpty()) end--;

                    var id = extractId(lines, start + 1, end);
                    var text = join(lines, start, end);
                    stanzas.add(new Stanza(type, id, start + 1, end + 1, text));
                    i = j;
                } else {
                    i++;
                }
            }
            return new OboDoc(frontMatter, stanzas);
        }

        private boolean isHeaderLine(String line) {
            var t = line.trim();
            return t.startsWith("[" ) && t.endsWith("]" );
        }

        private String extractType(String headerLine) {
            var t = headerLine.trim();
            return (t.startsWith("[" ) && t.endsWith("]" )) ? t.substring(1, t.length() - 1) : t;
        }

        private String extractId(List<String> lines, int from, int to) {
            for(var k = from; k <= to; k++) {
                var l = lines.get(k).trim();
                if(l.startsWith("id:" )) {
                    var id = l.substring(3).trim();
                    return id.isEmpty() ? null : id;
                }
            }
            return null;
        }

        private String join(List<String> lines, int from, int to) {
            var sb = new StringBuilder();
            for(var k = from; k <= to; k++) {
                sb.append(lines.get(k));
                if(k < to) sb.append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * Computes diffs between parsed docs.
     */
    static final class OboDiffer {

        private final DiffOptions opts;

        OboDiffer(DiffOptions opts) {
            this.opts = opts;
        }

        DiffResult diff(OboDoc before, OboDoc after) {
            var beforeFront = before.frontMatter();
            var afterFront = after.frontMatter();

            if(opts.stripIdenticalImports) {
                var stripped = removeIdenticalImports(beforeFront, afterFront);
                beforeFront = stripped[0];
                afterFront = stripped[1];
            }

            var frontChanged = !Objects.equals(beforeFront, afterFront);

            // By-id maps (skip stanzas without id)
            var bmap = index(before.stanzas());
            var amap = index(after.stanzas());

            Set<String> addedKeys = new HashSet<>(amap.keySet());
            addedKeys.removeAll(bmap.keySet());
            Set<String> removedKeys = new HashSet<>(bmap.keySet());
            removedKeys.removeAll(amap.keySet());
            Set<String> commonKeys = new HashSet<>(bmap.keySet());
            commonKeys.retainAll(amap.keySet());

            Map<String, Stanza> added = new LinkedHashMap<>();
            Map<String, Stanza> removed = new LinkedHashMap<>();
            Map<String, Stanza> changedOld = new LinkedHashMap<>();
            Map<String, Stanza> changedNew = new LinkedHashMap<>();

            for(var k : addedKeys) added.put(k, amap.get(k));
            for(var k : removedKeys) removed.put(k, bmap.get(k));

            for(var k : commonKeys) {
                var oldS = bmap.get(k);
                var newS = amap.get(k);
                if(!Objects.equals(Text.normalizeEol(oldS.text()), Text.normalizeEol(newS.text()))) {
                    changedOld.put(k, oldS);
                    changedNew.put(k, newS);
                }
            }

            if(opts.sortOutput) {
                added = sortByTypeId(added);
                removed = sortByTypeId(removed);
                changedOld = sortByTypeId(changedOld);
                changedNew = sortByTypeId(changedNew);
            }

            return new DiffResult(beforeFront, afterFront, added, removed, changedOld, changedNew, frontChanged);
        }

        private Map<String, Stanza> index(List<Stanza> stanzas) {
            Map<String, Stanza> m = new LinkedHashMap<>();
            for(var s : stanzas) {
                if(s.id() == null) continue; // only diff by-id
                m.put(key(s.type(), s.id()), s);
            }
            return m;
        }

        private String key(String type, String id) {
            return type + '\u0000' + id;
        }

        private Map<String, Stanza> sortByTypeId(Map<String, Stanza> in) {
            return in.values().stream()
                    .sorted(Comparator.comparing(Stanza::type)
                            .thenComparing(s -> s.id() == null ? "" : s.id()))
                    .collect(LinkedHashMap::new, (m, s) -> m.put(key(s.type(), s.id()), s), LinkedHashMap::putAll);
        }

        /**
         * If both front matters contain the same set of "import:" lines (order-insensitive),
         * remove those lines from both sides. Returns [beforeFront, afterFront], trimmed & LF-normalized.
         * This is useful because we can easily tell whether any imports changed – if the imports did
         * not change then we don't care about them when creating minimal ontologies.
         */
        private String[] removeIdenticalImports(String beforeFront, String afterFront) {
            var b = extractImports(beforeFront);
            var a = extractImports(afterFront);
            if(b.equals(a)) {
                beforeFront = removeLines(beforeFront, b);
                afterFront = removeLines(afterFront, a);
            }
            return new String[]{beforeFront.trim(), afterFront.trim()};
        }

        private Set<String> extractImports(String text) {
            Set<String> out = new HashSet<>();
            if(text == null || text.isEmpty()) return out;
            for(var line : text.split("\n" )) {
                var t = line.trim();
                if(t.startsWith("import:" )) out.add(t);
            }
            return out;
        }

        private String removeLines(String text, Set<String> linesToRemove) {
            if(text == null || text.isEmpty()) return text;
            var sb = new StringBuilder();
            for(var line : text.split("\n" )) {
                if(!linesToRemove.contains(line.trim())) {
                    sb.append(line).append('\n');
                }
            }
            return Text.stripTrailingBlankLines(sb.toString());
        }
    }

    /**
     * Renders BEFORE/AFTER files given a diff result.
     */
    static final class OboRenderer {

        String renderBefore(DiffResult r) {
            var sb = new StringBuilder();
            if(Text.hasText(r.beforeFront())) sb.append(r.beforeFront().strip()).append("\n\n" );
            for(var s : r.removed().values()) sb.append(s.text()).append("\n\n" );
            for(var s : r.changedOld().values()) sb.append(s.text()).append("\n\n" );
            return Text.trimEndLf(sb);
        }

        String renderAfter(DiffResult r) {
            var sb = new StringBuilder();
            if(Text.hasText(r.afterFront())) sb.append(r.afterFront().strip()).append("\n\n" );
            for(var s : r.added().values()) sb.append(s.text()).append("\n\n" );
            for(var s : r.changedNew().values()) sb.append(s.text()).append("\n\n" );
            return Text.trimEndLf(sb);
        }
    }

    /**
     * Small text utilities centralized for reuse and testing.
     */
    static final class Text {

        static String normalizeEol(String s) {
            if(s == null) return null;
            return s.replace("\r\n" , "\n" ).replace('\r', '\n');
        }

        static String stripTrailingBlankLines(String s) {
            if(s == null || s.isEmpty()) return "";
            var lines = Arrays.asList(s.split("\n" , -1));
            var end = lines.size() - 1;
            while(end >= 0 && lines.get(end).trim().isEmpty()) end--;
            return lines.subList(0, end + 1).stream().collect(Collectors.joining("\n" ));
        }

        static boolean hasText(String s) {
            return s != null && !s.isBlank();
        }

        static String trimEndLf(StringBuilder sb) {
            var len = sb.length();
            while(len > 0 && (sb.charAt(len - 1) == '\n' || sb.charAt(len - 1) == '\r')) len--;
            return sb.substring(0, len) + "\n";
        }
    }

    /**
     * One stanza of any type (e.g., [Term], [Typedef], custom headers).
     */
    public record Stanza(String type, String id, int startLine, int endLine, String text) {

    }

    /**
     * Parsed OBO document: front matter + stanzas.
     */
    public record OboDoc(String frontMatter, List<Stanza> stanzas) {

        public OboDoc(String frontMatter, List<Stanza> stanzas) {
            this.frontMatter = frontMatter;
            this.stanzas = List.copyOf(stanzas);
        }
    }

    /**
     * Options controlling diff / rendering. Fluent and immutable via builder.
     */
    public static final class DiffOptions {

        final boolean stripIdenticalImports;

        final boolean sortOutput;

        public DiffOptions() {
            this(true, true);
        }

        public DiffOptions(boolean stripIdenticalImports, boolean sortOutput) {
            this.stripIdenticalImports = stripIdenticalImports;
            this.sortOutput = sortOutput;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean stripIdenticalImports() {
            return stripIdenticalImports;
        }

        public boolean sortOutput() {
            return sortOutput;
        }

        public static final class Builder {

            private boolean stripIdenticalImports = true;

            private boolean sortOutput = true;

            public Builder stripIdenticalImports(boolean v) {
                this.stripIdenticalImports = v;
                return this;
            }

            public Builder sortOutput(boolean v) {
                this.sortOutput = v;
                return this;
            }

            public DiffOptions build() {
                return new DiffOptions(stripIdenticalImports, sortOutput);
            }
        }
    }

    /**
     * Output of a diff.
     */
    public static final class DiffResult {

        private final String beforeFront; // old

        private final String afterFront;  // new

        private final Map<String, Stanza> added;      // key = type + '\u0000' + id (from "after")

        private final Map<String, Stanza> removed;    // key = ...                 (from "before")

        private final Map<String, Stanza> changedOld; // old versions

        private final Map<String, Stanza> changedNew; // new versions

        private final boolean frontMatterChanged;

        DiffResult(String beforeFront, String afterFront,
                   Map<String, Stanza> added,
                   Map<String, Stanza> removed,
                   Map<String, Stanza> changedOld,
                   Map<String, Stanza> changedNew,
                   boolean frontMatterChanged) {
            this.beforeFront = beforeFront;
            this.afterFront = afterFront;
            this.added = Map.copyOf(added);
            this.removed = Map.copyOf(removed);
            this.changedOld = Map.copyOf(changedOld);
            this.changedNew = Map.copyOf(changedNew);
            this.frontMatterChanged = frontMatterChanged;
        }

        public String beforeFront() {
            return beforeFront;
        }

        public String afterFront() {
            return afterFront;
        }

        public Map<String, Stanza> added() {
            return added;
        }

        public Map<String, Stanza> removed() {
            return removed;
        }

        public Map<String, Stanza> changedOld() {
            return changedOld;
        }

        public Map<String, Stanza> changedNew() {
            return changedNew;
        }
    }
}
