import java.io.IOException;
import java.util.HashMap;

public class WebsiteInfo {
    public static void main(String[] args) throws IOException {
        Laborator1 l1 = new Laborator1("./stackoverflow/stackoverflow.html", "https://stackoverflow.com/");
        String textFile = l1.getTextFromHTML();
        System.out.println("Text salvat in fisierul \"" + textFile + "\".");
        HashMap<String, Integer> words = l1.processText(textFile);
        System.out.println("\nCuvintele din text sunt:");
        for (HashMap.Entry<String, Integer> word : words.entrySet())
        {
            System.out.println(word.getKey() + " -> " + word.getValue());
        }
    }
}
