package com.ali.scoring;

public class MatchUtil {
    /**
     * Sunday算法
     *
     * @param haystack
     * @param needle
     * @return
     */
    public int strStr(String haystack, String needle) {
        int L = needle.length(), n = haystack.length();

        for (int start = 0; start < n - L + 1; ++start) {
            if (haystack.substring(start, start + L).equals(needle)) {
                return start;
            }
        }
        return -1;
    }

    /**
     * error=1
     *
     * @param haystack
     * @param needle
     * @return
     */
    public boolean strStrError(String haystack, String needle) {
        int L = needle.length(), n = haystack.length();

        if (haystack.substring(n - L, n).equals(needle)) {
            return true;
        }
        return false;
    }

    /**
     * ! http.status_code=200
     *
     * @param haystack
     * @param needle
     * @return
     */
    public boolean strStrHttp(String haystack, String needle) {
        int L = needle.length(), n = haystack.length();

        if (haystack.substring(n - L - 3, n - 3).equals(needle)) {
            return true;
        }
        return false;
    }

}
