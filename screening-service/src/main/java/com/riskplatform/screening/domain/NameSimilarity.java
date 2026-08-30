package com.riskplatform.screening.domain;

/**
 * 名称相似度计算（R11.2）。
 *
 * <p>基于 Levenshtein 编辑距离的归一化相似度：similarity = 1 - distance / maxLen。
 * 大小写不敏感、忽略首尾空白。结果范围 [0,1]。
 */
public final class NameSimilarity {

    private NameSimilarity() {
    }

    /**
     * 计算两个名称的相似度。
     *
     * @return [0.0, 1.0]，两者均为空时返回 1.0；一方为空返回 0.0
     */
    public static double similarity(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() && y.isEmpty()) {
            return 1.0;
        }
        if (x.isEmpty() || y.isEmpty()) {
            return 0.0;
        }
        int distance = levenshtein(x, y);
        int maxLen = Math.max(x.length(), y.length());
        return 1.0 - ((double) distance / maxLen);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static int levenshtein(String s, String t) {
        int[] prev = new int[t.length() + 1];
        int[] curr = new int[t.length() + 1];
        for (int j = 0; j <= t.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= s.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= t.length(); j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[t.length()];
    }
}
