package cn.com.tcsl.ws.utils;

/**
 * Created by Tony zhu on 2018/11/12.
 */
public class LogUtils {

    private static boolean console_print_flag = true;

    public static void console_print(String content){
        if (console_print_flag){
            System.out.println(content);
        }

    }
    
    /**
     * The way to hide the console logs.
     * @param bool
     */
    public static void setConsolePringFlag(boolean bool){
    	console_print_flag = bool;
    }

}
