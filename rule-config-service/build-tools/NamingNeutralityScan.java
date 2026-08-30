/*
 * 命名中性化构建期扫描器（后端，risk-console-redesign）。
 *
 * 单文件、零第三方依赖的 Java 程序，经 exec-maven-plugin 在 validate 阶段以
 * JDK 单文件源码启动模式（java NamingNeutralityScan.java ...）运行。
 *
 * 扫描本期新增的后端代码/资源（见 naming-scan.config.properties 的 scanRoots，
 * 以及 V{minMigrationVersion}+ 起的 Flyway 迁移与 R__seed_* 种子脚本），
 * 命中厂商专有名词清单（vendorTerms 配置，大小写不敏感）即报告
 * 出现位置（文件:行:列 + 命中词）并以非零退出码使构建失败。
 *
 * 旧子域（扩展阶段产出的 V13-V18 迁移及相关 Java）不在 scanRoots
 * 范围内，故不会因历史命名而误报。后续任务新增本期子域/资源时，把其路径补充到
 * naming-scan.config.properties 的 scanRoots 即可（可复用、可配置）。
 *
 * Validates: Requirements 1.1, 1.3, 1.4
 *
 * 用法：java NamingNeutralityScan.java <baseDir> [configFile]
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class NamingNeutralityScan {

    static final String[] DEFAULT_TERMS = {};
    static final Set<String> SCAN_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".java", ".sql", ".xml", ".yml", ".yaml", ".properties", ".json", ".txt", ".md"));

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("[naming-scan-backend] 用法: java NamingNeutralityScan.java <baseDir> [configFile]");
            System.exit(2);
        }
        Path baseDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path configFile = args.length >= 2
                ? Paths.get(args[1])
                : baseDir.resolve("build-tools/naming-scan.config.properties");

        Properties cfg = new Properties();
        if (Files.exists(configFile)) {
            try (var in = Files.newInputStream(configFile)) {
                cfg.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } else {
            System.err.println("[naming-scan-backend] 配置文件不存在: " + configFile);
            System.exit(2);
        }

        List<String> terms = splitCsv(cfg.getProperty("vendorTerms", String.join(",", DEFAULT_TERMS)));
        if (terms.isEmpty()) terms = Arrays.asList(DEFAULT_TERMS);
        List<String> scanRoots = splitCsv(cfg.getProperty("scanRoots", ""));
        String migrationDir = cfg.getProperty("migrationDir", "").trim();
        int minMigrationVersion = parseIntSafe(cfg.getProperty("minMigrationVersion", "0"));
        String seedPrefix = cfg.getProperty("seedPrefix", "R__seed_").trim();

        List<Path> targets = new ArrayList<>();
        for (String root : scanRoots) {
            Path p = baseDir.resolve(root.trim()).normalize();
            collect(p, targets);
        }
        // 迁移目录：仅纳入版本号 >= minMigrationVersion 的 V 迁移与 R__seed_* 种子（本期资产）。
        if (!migrationDir.isEmpty()) {
            Path mdir = baseDir.resolve(migrationDir).normalize();
            if (Files.isDirectory(mdir)) {
                try (var ds = Files.newDirectoryStream(mdir)) {
                    for (Path f : ds) {
                        String name = f.getFileName().toString();
                        if (isInScopeMigration(name, minMigrationVersion, seedPrefix)) {
                            targets.add(f);
                        }
                    }
                }
            }
        }

        List<String> findings = new ArrayList<>();
        for (Path file : targets) {
            scanFile(file, baseDir, terms, findings);
        }

        if (!findings.isEmpty()) {
            System.err.println("[naming-scan-backend] 检测到厂商专有名词，命名中性化检查不通过"
                    + "（Requirement 1.1/1.3/1.4）：\n");
            for (String f : findings) System.err.println("  " + f);
            System.err.println("\n[naming-scan-backend] 共 " + findings.size()
                    + " 处命中。请改用中性的\"风控/反欺诈平台\"命名。");
            System.exit(1);
        }
        System.out.println("[naming-scan-backend] 通过：已扫描 " + targets.size()
                + " 个文件，未发现厂商专有名词 (" + String.join(", ", terms) + ")。");
    }

    /** 判断迁移目录下的文件是否属于本期扫描范围（V>=min 的版本迁移或 R__seed_* 种子）。 */
    static boolean isInScopeMigration(String name, int minVersion, String seedPrefix) {
        if (!seedPrefix.isEmpty() && name.startsWith(seedPrefix)) return true;
        if (name.startsWith("V")) {
            int us = name.indexOf("__");
            if (us > 1) {
                try {
                    int version = Integer.parseInt(name.substring(1, us));
                    return version >= minVersion;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    static void collect(Path p, List<Path> out) throws IOException {
        if (!Files.exists(p)) return; // 未来任务才创建的路径：跳过而非报错
        if (Files.isRegularFile(p)) {
            out.add(p);
            return;
        }
        Files.walkFileTree(p, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String n = file.getFileName().toString();
                int dot = n.lastIndexOf('.');
                String ext = dot >= 0 ? n.substring(dot) : "";
                if (SCAN_EXTENSIONS.isEmpty() || SCAN_EXTENSIONS.contains(ext)) {
                    out.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void scanFile(Path file, Path baseDir, List<String> terms, List<String> findings) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // 非 UTF-8 / 不可读：跳过
        }
        String rel = baseDir.relativize(file).toString();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase();
            for (String term : terms) {
                if (term.isEmpty()) continue;
                String needle = term.toLowerCase();
                int from = 0, idx;
                while ((idx = lower.indexOf(needle, from)) >= 0) {
                    findings.add(rel + ":" + (i + 1) + ":" + (idx + 1)
                            + "  命中 \"" + term + "\"  → " + line.trim());
                    from = idx + 1;
                }
            }
        }
    }

    static List<String> splitCsv(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
