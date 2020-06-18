package Laboratorul3;

import com.google.gson.reflect.TypeToken;

import Lab2.ExceptionList;
import Lab2.StopWordList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;

public class Laboratorul3 {
    private String folder_website;
    private String baseUri;

   public Laboratorul3(String folder_website, String baseUri)
    {
        this.folder_website = folder_website;
        this.baseUri = baseUri;
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
        } 
        else 
        {
        	descriere_sir = descriere.attr("content");
        }
        return descriere_sir;
    }

    private String robots(Document document) // se obtine lista robots
    {
        String roboti_sir = "";
        Element roboti = document.selectFirst("meta[name=robots]");

        if (roboti == null) 
        {
            System.out.println("Nu s-a putut gasi tag-ul <meta name=\"robots\">!");
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
        return cale;
    }

    public File preluareText(Document document, File html_fisier) throws IOException // se ia text din doc si se pune intr-un fisier
    {
        StringBuilder sb = new StringBuilder();
        sb.append(titlu(document)); // titlul
        sb.append(cuvinte_cheie(document)); // cuvintele cheie
        sb.append(descriere(document));
        sb.append(document.body().text());
        String text = sb.toString();
        
        StringBuilder text_nume = new StringBuilder(html_fisier.getAbsolutePath()); //generare de num fisier + .txt       
        text_nume.append(".txt"); //adauga extensie txt        
        String text_nume_fisier = text_nume.toString();
        FileWriter fw = new FileWriter(new File(text_nume_fisier), false); //se scrie rez in fisier
        fw.write(text);
        fw.close();
        return new File(text_nume_fisier);
    }

    public HashMap<String,Integer> procesare_text(String fileName) throws IOException 
    { // se ia text din fisier si se returneaza lista cuvintelor
        int c; // caracter curent
        HashMap<String,Integer> lista_cuvinte = new HashMap<String,Integer>();       
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
                    if (!lista_cuvinte.containsKey(cuvant_nou)) 
                    {
                    	lista_cuvinte.put(cuvant_nou,lista_cuvinte.get(cuvant_nou)+1);
                    }else
                    {
                    	lista_cuvinte.put(cuvant_nou,1);
                    }
                }
                else if (StopWordList.stopwords.contains(cuvant_nou))
                {// daca e stopword se ignora
                    string_builder.setLength(0);
                    continue;
                }
                else // cuv de dictionar
                {
                    // stemming 
                    if (lista_cuvinte.containsKey(cuvant_nou))
                    {
                    	lista_cuvinte.put(cuvant_nou,lista_cuvinte.get(cuvant_nou)+1); //contorizare nr aparitii
                    }
                    else
                    {
                    	lista_cuvinte.put(cuvant_nou,1); //se adauga cuv
                    }
                }
                string_builder.setLength(0); //se elimina tot din sb
            }
            else //in cazul in care ne aflam in centrul cuvantului
            {
            	string_builder.append((char)c); // se adauga litera curenta la cuv 
            }
        }            
        // scrie in fisier lista de cuvinte+nr aparitii 
        //INDEX DIRECT
        StringBuilder string_builder_DirectIndex_nume_fisier = new StringBuilder(fileName);
//fisier cu extensia ".Index_Direct.json" 
        string_builder_DirectIndex_nume_fisier.replace(string_builder_DirectIndex_nume_fisier.lastIndexOf(".") + 1, string_builder_DirectIndex_nume_fisier.length(), "Index_Direct.json");
        Writer scris = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(string_builder_DirectIndex_nume_fisier.toString()), "utf-8"));

        Gson gsonBuilder = new GsonBuilder().setPrettyPrinting().create();
        String jsonFile = gsonBuilder.toJson(lista_cuvinte); 
        scris.write(jsonFile);
        scris.close();         
        inputStream.close();       
        return lista_cuvinte;
    }
    
    
    
    

    
    // calculeaza indexul direct
    public HashMap<String, HashMap<String, Integer>> Index_Direct() throws IOException
    {
        HashMap<String, HashMap<String, Integer>> directIndex = new HashMap<>();
        Gson gsonBuilder = new GsonBuilder().setPrettyPrinting().create(); //cream gson builder
        Writer fisier_map_scriere = new BufferedWriter(new OutputStreamWriter(         //aici se va obtine fisierul de mapare
                new FileOutputStream(folder_website + "Index_Direct.map"), "utf-8"));  //se cheama Index_Direct.map din directorul principal
        HashMap<String, String> mapFile = new HashMap<>();
        LinkedList<String> coada_folder = new LinkedList<>();  //coada pt parcurgere foldere
        coada_folder.add(folder_website); //folder principal

        while (!coada_folder.isEmpty()) 
        { //dupa ce s-a terminat parcurgere folderelor
            String currentFolder = coada_folder.pop(); //se ia din coada foldere
            File folder = new File(currentFolder);
            File[] listOfFiles = folder.listFiles();

            try  //parcurgere lista de foldere
            {
                for (File file : listOfFiles) //pentru fiecare fisier din foldere se parcurge lista de foldere
                { //verif daca fisierul este html
                    if (file.isFile() && Files.probeContentType(file.toPath()).equals("text/html")) 
                    {
                        Document doc = Jsoup.parse(file, null, baseUri); //parsare
                        String fileName = file.getAbsolutePath();

                        File textFile = preluareText(doc, file); //stocare/preluare text in alt fisier
                        String textFileName = textFile.getAbsolutePath();

                        HashMap<String, Integer> currentDocWords = procesare_text(textFileName); //procesare cuvinte si va afisa cuvantul si nr. de aparitii

                        directIndex.put(fileName, currentDocWords); //se adauga doc+cuvinte in hashmap

                        // adaugam doc curent+index direct fisierul de mapare
                        mapFile.put(fileName, fileName + ".Index_Direct.json"); //se pune rezultatul in fisier cu extensia .Index_Direct.json
                    }
                    else if (file.isDirectory()) 
                    { //daca e director(folder) se pune in coada
                    	coada_folder.add(file.getAbsolutePath());
                    }
                }
            } catch (NullPointerException e) 
            {
                System.out.println("Nu s-au gasit fisiere in folder");
            }
        }
        fisier_map_scriere.write(gsonBuilder.toJson(mapFile)); // se scrie fis de mapare + string json
        fisier_map_scriere.close();
        return directIndex;
    }
          
    public TreeMap<String, HashMap<String, Integer>> Index_Indirect() throws IOException
    {
        TreeMap<String, HashMap<String, Integer>> Index_Indirect = new TreeMap<>();
        Gson gsonBuilder = new GsonBuilder().setPrettyPrinting().create();      
        Writer fisier_mapare_scris = new BufferedWriter(new OutputStreamWriter( //obtinem fisierul de mapre Index_Indirect.map din folderul radacine
                new FileOutputStream(folder_website + "Index_Indirect.map"), "utf-8"));
        HashMap<String, String> mapFile = new HashMap<>();
        LinkedList<String> coada_folder = new LinkedList<>(); //parcurgem directoarele
        coada_folder.add(folder_website); //se incepe cu folder principal

        while (!coada_folder.isEmpty()) // cat timp nu mai sunt foldere ce trebuie parcurse
        {
            String currentFolder = coada_folder.pop(); //se ia folderul
            File folder = new File(currentFolder);
            File[] listOfFiles = folder.listFiles();
            try {
                for (File file : listOfFiles)  // se parcurg folderele
                {
                    if (file.isFile() && file.getAbsolutePath().endsWith(".Index_Direct.json")) //verif daca e fisier index direct
                    {
                        String fileName = file.getAbsolutePath();
                        String nume_document = fileName.replace(".Index_Direct.json", "");

                        Type directIndexType = new TypeToken<HashMap<String, Integer>>(){}.getType();  // se ia json-ul cu index direct + parsare
                        HashMap<String, Integer> directIndex = gsonBuilder.fromJson(new String(Files.readAllBytes(file.toPath())), directIndexType);

                        TreeMap<String, HashMap<String, Integer>> localIndirectIndex = new TreeMap<>(); //stocare index indirect 
                        for(Map.Entry<String, Integer> entry : directIndex.entrySet()) //stocare nr aparitii pt fiecare cuvant+doc din care face parte
                        {
                            String cuvant = entry.getKey();
                            int numar_de_aparitii = entry.getValue();
                            if (localIndirectIndex.containsKey(cuvant))  //se adauga intrare in treemap
                            { //daca cuv este deja in treemap
                                HashMap<String, Integer> apparitions = localIndirectIndex.get(cuvant); //adaugare in vector
                                apparitions.put(nume_document, numar_de_aparitii);
                            }
                            else
                            {
                                HashMap<String, Integer> apparitions = new HashMap<>();
                                apparitions.put(nume_document, numar_de_aparitii);
                                localIndirectIndex.put(cuvant, apparitions);
                            }

                            if (Index_Indirect.containsKey(cuvant)) // se adauga intrare in treemap final
                            {//daca cuv este in treemap
                                // il adaugam in vectorul de aparitii
                                HashMap<String, Integer> apparitions = Index_Indirect.get(cuvant);
                                apparitions.put(nume_document, numar_de_aparitii);
                            }
                            else
                            {
                                HashMap<String, Integer> apparitions = new HashMap<>();
                                apparitions.put(nume_document, numar_de_aparitii);
                                Index_Indirect.put(cuvant, apparitions);
                            }
                        }
                        Writer writer = new BufferedWriter(new OutputStreamWriter( //se scrie JSONul cu indexul indirect
                                new FileOutputStream(nume_document + ".Index_Indirect.json"), "utf-8"));
                        writer.write(gsonBuilder.toJson(localIndirectIndex));
                        writer.close();

                        mapFile.put(nume_document, nume_document + ".Index_Indirect.json"); //se adauga doc curent+index indirect in fisier mapare
                    }
                    else if (file.isDirectory()) 
                    { //daca e folder se pune in coada
                    	coada_folder.add(file.getAbsolutePath());
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("nu s-a gasit fisier in folder");
            }
        }
        Writer indirectIndexWriter = new BufferedWriter(new OutputStreamWriter( //se face folder concatenat de index indirect
                new FileOutputStream(folder_website + "Index_Indirect.json"), "utf-8"));
        indirectIndexWriter.write(gsonBuilder.toJson(Index_Indirect));
        fisier_mapare_scris.write(gsonBuilder.toJson(mapFile)); //se scrie folder de mapare cu string JSON creat
        fisier_mapare_scris.close();
        indirectIndexWriter.close();
        System.out.println("s-a facut maparea");
        return Index_Indirect;  
    }
   
}

    
    

    
    

