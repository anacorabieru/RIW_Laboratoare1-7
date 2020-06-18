package Laboratorul3;

import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;

public class Index {
    public static void main(String[] args) throws IOException {
        Laboratorul3 l3 = new Laboratorul3("./documente/","https://stackoverflow.com/");
        HashMap<String, HashMap<String, Integer>> Index_Direct = l3.Index_Direct();
        TreeMap<String, HashMap<String, Integer>> Index_Indirect = l3.Index_Indirect();
    }
}
