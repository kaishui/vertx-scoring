package com.ali.scoring.config;

import com.ali.scoring.controller.CommonController;

import java.security.MessageDigest;

public class Utils {

    public static long toLong(String str, long defaultValue) {
        if (str == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException var4) {
                return defaultValue;
            }
        }
    }

    public static String MD5(String key) {
        char hexDigits[] = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        try {
            byte[] btInput = key.getBytes();
            // 获得MD5摘要算法的 MessageDigest 对象
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            // 使用指定的字节更新摘要
            mdInst.update(btInput);
            // 获得密文
            byte[] md = mdInst.digest();
            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isClientProcess() {
        String port = System.getProperty("server.port", "8080");
        if (Constants.CLIENT_PROCESS_PORT1.equals(port) ||
                Constants.CLIENT_PROCESS_PORT2.equals(port)) {
            return true;
        }
        return false;
    }

    public static boolean isBackendProcess() {
        String port = System.getProperty("server.port", "8080");
        if (Constants.BACKEND_PROCESS_PORT1.equals(port)) {
            return true;
        }
        return false;
    }

    public static String sendToApiPort() {
        String port = System.getProperty("dev", CommonController.getDataSourcePort());
        return port;
    }

    public static String getErrorTracePath() {
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port)) {
            return "get-error-traceIds-8001";
        } else if ("8001".equals(port)) {
            return "get-error-traceIds-8000";
        } else {
            return null;
        }
    }

    public static String getWrongPath() {
        String port = System.getProperty("server.port", "8080");
        if ("8000".equals(port)) {
            return "getWrongTrace8001";
        } else {
            return "getWrongTrace8000";
        }
    }
}
