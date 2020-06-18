package Lab2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.nio.file.Files;

public class Laboratorul2 {
    private String folder_website;
    private String baseUri;
    private boolean fisiere_parsate_html;

    Laboratorul2(String htmlFile, String baseUri) throws IOException
    {
        this.folder_website = folder_website;
        this.baseUri = baseUri;
        fisiere_parsate_html = false;
    }

    private String titlu(Document document) // se obtine titlul documentului
    {
        String title = document.title();

        return title;
    }

    private String cuvinte_cheie(Document document) // se iau acele key words
    {
        String cuvinte_cheie_sir = "";
        Element cuvinte_cheie = document.selectFirst("meta[name=keywords]");
        
        if (cuvinte_cheie == null) 
        {
            System.out.println("Nu s-au gasit");
        } 
        else 
        {
        	cuvinte_cheie_sir = cuvinte_cheie.attr("content");
        }
        return cuvinte_cheie_sir;
    }

    private String descriere(Document document) // se ia descierea
    {
        String descriere_sir = "";
        Element descriere = document.selectFirst("meta[name=description]");

        if (descriere == null) 
        {
            System.out.println("Nu s-au gasit");
        } 
        else 
        {
        	descriere_sir = descriere.attr("content");
            System.out.println("Descrierea a fost luata ");
        }
        return descriere_sir;
    }

    private String robots(Document document) // se obtine lista robots
    {
        String roboti_sir = "";
        Element roboti = document.selectFirst("meta[name=robots]");

        if (roboti == null) 
        {
            System.out.println("Nu s-au gasit");
        } 
        else 
        {
        	roboti_sir = roboti.attr("content");
        }
        return roboti_sir;
    }

    private Set<String> linkuri(Document document) throws IOException // se preiau linkurile
    {
        Set<String> cale = new HashSet<String>();   //cale=url
        
        Elements linkuri = document.select("a[href]");

        for (Element link : linkuri) 
        {
            String link_abs = link.attr("abs:href"); // transformam link relativ in absolut
            if (link_abs.contains(baseUri))  {continue;}// se ignora legaturi interne

            int pozitie_ancora = link_abs.indexOf('#'); //se cauta ancorele #
            if (pozitie_ancora != -1) 
            {   // in cazul existentei unei ancore
                StringBuilder linkul_scris = new StringBuilder(link_abs); //se sterge ancora din link
                linkul_scris.replace(pozitie_ancora, linkul_scris.length(), "");
                link_abs = linkul_scris.toString();
            }
            cale.add(link_abs);
        }
   //     System.out.println("S-au obtinul linkurile");
        return cale;
    }

    public String preluareText(Document document, File html_fisier) throws IOException // se ia text din doc si se pune intr-un fisier
    {
        StringBuilder sb = new StringBuilder();
        sb.append(titlu(document)); // titlul
        sb.append(cuvinte_cheie(document)); // cuvintele cheie
        sb.append(descriere(document));
        sb.append(document.body().text());
        String text = sb.toString();
        
        StringBuilder text_nume = new StringBuilder(html_fisier.getAbsolutePath()); //generare de num fisier + .txt
        
        if (text_nume.indexOf("?") != -1) // daca are ? fisierul html primexte .txt si numele 
        {
        	text_nume.append(".txt");
        }
        else // else se inlocuieste extensia de dupa punct cu txt
        {
        	text_nume.replace(text_nume.lastIndexOf(".") + 1, text_nume.length(), "txt");
        }
        String textFileName = text_nume.toString();

        FileWriter fw = new FileWriter(new File(textFileName), false); //se scrie rez in fisier
        fw.write(text);
        fw.close();

        return textFileName;
    }

    public List<String> procesare_text(String fileName) throws IOException 
    { // se ia text din fisier si se returneaza lista cuvintelor
        int c; // caracter curent
        List<String> lista_cuvinte = new LinkedList<>();
        
        FileReader inputStream = null; //se citeste litera cu litera / caracter cu caracter
        inputStream = new FileReader(fileName);
        StringBuilder string_builder = new StringBuilder(); //delimitatori(paranteze, semne)

        while ((c = inputStream.read()) != -1)
        {
            if (!Character.isLetterOrDigit((char)c)) 
            { // intalnim delimitator si formam un cuv noi
                String cuvant_nou = string_builder.toString(); 
                if (ExceptionList.exceptions.contains(cuvant_nou))
                {
                    if (!lista_cuvinte.contains(cuvant_nou)) 
                    {
                    	lista_cuvinte.add(cuvant_nou);
                    }
                }
                // apoi daca este stopword
                else if (StopWordList.stopwords.contains(cuvant_nou))
                {
                    // il ignoram
                    string_builder.setLength(0);
                    continue;
                }
                else // cuvant de dictionar
                {
                    // stemming 
                    if (!lista_cuvinte.contains(cuvant_nou))
                    {
                    	lista_cuvinte.add(cuvant_nou);
                    }
                }
                string_builder.setLength(0);
            }
            else //in cazul in care ne aflam in centrul cuvantului
            {
            	string_builder.append((char)c); // se adauga litera curenta la cuv 
            }
        }
            
            // scriem in fisier lista de cuvinte
        Writer writer = new BufferedWriter(new OutputStreamWriter(
           new FileOutputStream(fileName + ".words"), "utf-8"));
        for (String word : lista_cuvinte)
        {
            writer.write(word + "\n");
        }
            writer.close();
        inputStream.close();
        System.out.println("s-au obtinut cuvintele");
        return lista_cuvinte;
    }
    // cauta fisierele HTML din website si extrage textul din ele
    public void text_from_HTMLfiles() throws IOException
    {
        LinkedList<String> coada_folder = new LinkedList<>(); // se parcurg folderele cu o coada
        coada_folder.add(folder_website); //de la folder radacine

        while (!coada_folder.isEmpty()) // cat timp nu mai sunt foldere de parcurs
        {
            String currentFolder = coada_folder.pop(); //se ia un folder din coada
            File folder = new File(currentFolder);
            File[] listOfFiles = folder.listFiles();

            try { // se parcurge lista de foldere
                for (int i = 0; i < listOfFiles.length; i++)
                {
                    File file = listOfFiles[i];

                    // daca gaseste fisier se va verif daca e html
                    if (file.isFile() && Files.probeContentType(file.toPath()).equals("text/html"))
                    {
                        Document doc = Jsoup.parse(file, null, baseUri); //parsare fisier

                        preluareText(doc, file); //stocare text in fisier separat

                        System.out.println("Am procesat fisierul HTML \"" + file.getAbsolutePath() + "\".");
                    }
                    else if (file.isDirectory()) // daca este folder, il punem in coada
                    {
                    	coada_folder.add(file.getAbsolutePath());
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("nu sunt fisiere in folderul \"" + currentFolder + "\"");
            }
        }

        fisiere_parsate_html = true;
    }

    public void getWordsFromTextFiles() throws IOException 
    {//parseaza fisiere text obtinute dupa extraferea din html si ia si prelucreaza cuvinte
        if (!fisiere_parsate_html)
        {
        	text_from_HTMLfiles();
        }

        LinkedList<String> folderQueue = new LinkedList<>();

        folderQueue.add(folder_website);

        while (!folderQueue.isEmpty()) // cat timp nu mai sunt foldere copil de parcurs
        {
            // preluam un folder din coada
            String currentFolder = folderQueue.pop();
            File folder = new File(currentFolder);
            File[] listOfFiles = folder.listFiles();

            // ii parcurgem lista de fisiere 
            try {
                for (int i = 0; i < listOfFiles.length; i++)
                {
                    File file = listOfFiles[i];

                    if (file.isFile() && file.getAbsolutePath().endsWith(".txt")) // se verif sa fie fisier cu extensia txt
                    {
                    	procesare_text(file.getAbsolutePath());
                        System.out.println("s-a procesat fisierul text \"" + file.getAbsolutePath() + "\"");
                    }
                    else if (file.isDirectory()) // daca este folder, il punem in coada
                    {
                        folderQueue.add(file.getAbsolutePath());
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("nu sunt fisiere in folderul \"" + currentFolder + "\"!");
            }
        }
    }
    
    
    
}

