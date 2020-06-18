import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;

public class Laborator1 {
    private File html;
    private String baseUri;
    private Document doc;

    Laborator1(String htmlFile, String baseUri) throws IOException
    {
        // preluam fisierul cu pagina web de pe disc
        html = new File(htmlFile);

        // parsam fisierul HTML folosind JSOUP
        doc = Jsoup.parse(html, null, baseUri);

        this.baseUri = baseUri;
    }

    private String getTitle() // preia titlul documentului
    {
        String title = doc.title();
        System.out.println("Titlul site-ului: " + title);
        return title;
    }

    private String getKeywords() // preia cuvintele cheie
    {
        Element keywords = doc.selectFirst("meta[name=keywords]");
        String keywordsString = "";
        if (keywords == null) {
            System.out.println("Nu exista tag-ul <meta name=\"keywords\">!");
        } else {
            keywordsString = keywords.attr("content");
            System.out.println("Cuvintele cheie au fost preluate!");
        }
        return keywordsString;
    }

    private String getDescription() // preia descrierea site-ului
    {
        Element description = doc.selectFirst("meta[name=description]");
        String descriptionString = "";
        if (description == null) {
            System.out.println("Nu exista tag-ul <meta name=\"description\">!");
        } else {
            descriptionString = description.attr("content");
            System.out.println("Descrierea site-ului a fost preluata!");
        }
        return descriptionString;
    }

    private String getRobots() // preia lista de robots
    {
        Element robots = doc.selectFirst("meta[name=robots]");
        String robotsString = "";
        if (robots == null) {
            System.out.println("Nu exista tag-ul <meta name=\"robots\">!");
        } else {
            robotsString = robots.attr("content");
            System.out.println("Lista de robots a site-ului a fost preluata!");
        }
        return robotsString;
    }

    private Set<String> getLinks() throws IOException // preia link-urile de pe site (ancorele)
    {
        Elements links = doc.select("a[href]");
        Set<String> URLs = new HashSet<String>();
        for (Element link : links) {
            String absoluteLink = link.attr("abs:href"); // facem link-urile relative sa fie absolute
            if (absoluteLink.contains(baseUri)) // ignoram legaturile interne
            {
                continue;
            }

            // cautam eventuale ancore in link-uri
            int anchorPosition = absoluteLink.indexOf('#');
            if (anchorPosition != -1) // daca exista o ancora (un #)
            {
                // stergem partea cu ancora din link
                StringBuilder tempLink = new StringBuilder(absoluteLink);
                tempLink.replace(anchorPosition, tempLink.length(), "");
                absoluteLink = tempLink.toString();
            }

            // nu vrem sa adaugam duplicate, asa incat folosim o colectie de tip Set
            URLs.add(absoluteLink);
        }
        System.out.println("Link-urile de pe site au fost preluate!");
        return URLs;
    }

    public String getTextFromHTML() throws IOException // preia textul din document si il pune intr-un fisier
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getTitle()); // titlul
        sb.append(System.lineSeparator());
        sb.append(getKeywords()); // cuvintele cheie
        sb.append(System.lineSeparator());
        sb.append(getDescription());
        sb.append(System.lineSeparator());
        sb.append(doc.body().text());
        String text = sb.toString();

        // generam numere fisierului text corespunzator, cu extensia txt
        StringBuilder textFileNameBuilder = new StringBuilder(html.getAbsolutePath());
        textFileNameBuilder.replace(textFileNameBuilder.lastIndexOf(".") + 1, textFileNameBuilder.length(), "txt");
        String textFileName = textFileNameBuilder.toString();

        // scriem rezultatul in fisierul text
        FileWriter fw = new FileWriter(new File(textFileName), false);
        fw.write(text);
        fw.close();

        return textFileName;
    }

    // preia textul din fisier, caracter cu caracter si returneaza lista de cuvinte
    public HashMap<String, Integer> processText(String fileName) throws IOException
    {
        HashMap<String, Integer> hashText = new HashMap<String, Integer>();

        // citim din fisier caracter cu caracter
        FileReader inputStream = null;
        inputStream = new FileReader(fileName);

        // String delimiters = " \t\",.?!;:()[]{}@&#^%'`~<>/\\-–_|„”“=+*";
        StringBuilder sb = new StringBuilder();
        int c; // caracterul curent
        while ((c = inputStream.read()) != -1)
        {
            // if (delimiters.indexOf(textChars[i]) != -1) // suntem pe un separator
            if (!Character.isLetterOrDigit((char)c)) // suntem pe un separator
            {
                String newWord = sb.toString(); // cream cuvantul nou
                if (hashText.containsKey(newWord)) // daca exista deja in HashMap
                {
                    hashText.put(newWord, hashText.get(newWord) + 1); // incrementam numarul de aparitii
                }
                else // daca nu, il adaugam
                {
                    hashText.put(newWord, 1);
                }
                // System.out.println(newWord + " -> " + hashText.get(newWord));
                sb.setLength(0); // curatam StringBuilder-ul
            }
            else // suntem in mijlocul unui cuvant
            {
                sb.append((char)c); // adaugam litera curenta la cuvantul ce se creeaza
            }
        }

        // eliminam cuvantul vid
        hashText.remove("");

        inputStream.close();
        System.out.println("Cuvintele din textul de pe site au fost prelucrate!");

        return hashText;
    }
}
