import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Crawler {
    private HTTPClient httpClient;
    private LinkedList<String> Q;
    private HashSet<String> visited;
    private HashMap<String, String> robots;
    private HashMap<String, Long> requestTimesPerDomain;
    private HashMap<String, Integer> numberOfRedirectsPerDomain;
    private HashMap<String, Integer> numberOfNotFoundPerDomain;

    public Crawler(String userAgent)
    {
        // initializam clientul HTTP
        httpClient = new HTTPClient(userAgent, "./http");

        // -------------------------------- URL FRONTIER -------------------------------
        // initializam coada de URL-uri
        Q = new LinkedList<>();
        // -----------------------------------------------------------------------------

        // initializam multimea de link-uri vizitate
        visited = new HashSet<>();

        // initializam lista de reguli robots
        robots = new HashMap<>();

        // initializam structura de date ce contine ultimul moment in care s-a facut o cerere pe un domeniu
        requestTimesPerDomain = new HashMap<>();

        // initializam structura de date in care se pun perechi <domeniu, nr de redirect-uri> (folosita impotriva Spider Trap)
        numberOfRedirectsPerDomain = new HashMap<>();

        // initializam structura de date in care se pun perechi <domeniu, nr de erori 404> (folosita impotriva Spider Trap)
        numberOfNotFoundPerDomain = new HashMap<>();
    }

    // sterge un domeniu din coada de explorare a crawler-ului
    private void removeDomain(String domain)
    {
        String url = "http://" + domain;

        // coada de explorare
        Q.removeIf(entry -> entry.contains(url));

        // URL-uri vizitate
        visited.removeIf(entry -> entry.contains(url));

        // lista de robots
        robots.entrySet().removeIf(entry -> entry.getKey().contains(url));

        // timpi de cerere / domeniu
        requestTimesPerDomain.entrySet().removeIf(entry -> entry.getKey().contains(url));

        // numar de redirectionari / domeniu
        numberOfRedirectsPerDomain.entrySet().removeIf(entry -> entry.getKey().contains(url));

        // numar de erori 404 / domeniu
        numberOfNotFoundPerDomain.entrySet().removeIf(entry -> entry.getKey().contains(url));
    }

    public void Crawl(String startingURL, int maxPages)
    {
        // -------------------------- MASURARE TIMPI / VITEZA --------------------------
        long globalStartTime = System.nanoTime(); // timpul global
        int numberOfPagesCrawled = 0;
        // -----------------------------------------------------------------------------

        // initializam URL Frontier, cu primul URL
        Q.add(startingURL);

        // cat timp coada contine pagini si nu a fost atinsa limita maxima de pagini
        while (!Q.isEmpty() && numberOfPagesCrawled < maxPages)
        {
            String L = Q.pop(); // L = un URL din Q
            // System.out.println("[URL_FRONTIER] Preluat URL din coada: " + L);

            // ----------------------------------- FETCH -----------------------------------
            // descarcam continutul lui L
            // trebuie sa parsam URL-ul paginii
            try {

                URL currentURL = new URL(L);

                // ---------------------------------- PARSE ----------------------------------
                int port = currentURL.getPort();
                if (port == -1) // trecem pe portul implicit in caz ca portul nu poate fi identificat
                {
                    port = 80;
                }
                String domain = currentURL.getHost();
                String path = currentURL.getPath();
                if (path.equals(""))
                {
                    path = "/";
                }

                if (!currentURL.getProtocol().equals("http")) // daca protocolul nu e HTTP, ignoram URL-ul
                {
                    System.out.println("[PROTOCOL_INVALID] URL: " + L);
                    continue;
                }
                // --------------------------------------------------------------------------

                try {
                    // ----------------------- POLITETE (REP - VITEZA DE EXPLORARE + robots.txt) -----------------------
                    // inainte de cererea HTTP, respectam viteza minima de aducere a paginilor / domeniu
                    if (!robots.containsKey(domain)) // domeniu nou de explorat
                    {
                        // descarcam robots.txt de pe acest domeniu
                        String robotsRules = httpClient.getResource("/robots.txt", domain, port, 0);

                        if (robotsRules != null) // si stocam regulile din fisier, daca acestea exista
                        {
                            byte[] encoded = Files.readAllBytes(Paths.get(robotsRules));
                            String robotsText = new String(encoded);

                            // adaugam setul de reguli pe acest domeniu
                            robots.put(domain, robotsText);
                        }
                        else if (httpClient.getLastStatus() == 4) // nu s-a putut descarca robots.txt
                        {
                            removeDomain(domain);
                            continue;
                        }
                        else if (httpClient.getLastStatus() == 404) // nu exista robots.txt pe server
                        {
                            robots.put(domain, null);
                        }

                        // stocam momentul cand am accesat domeniul ultima data
                        requestTimesPerDomain.put(domain, System.nanoTime());
                    }
                    else
                    {
                        // domeniul a fost explorat anterior, verificam daca a trecut suficient timp pana sa exploram din nou
                        double timeBetweenRequests = (double)(System.nanoTime() - requestTimesPerDomain.get(domain)) / 1000000000.0; // calculam timpul scurs intre cereri
                        if (timeBetweenRequests < 1) // daca nu a trecut cel putin 1 secunda, punem URL-ul inapoi in coada si il luam pe urmatorul
                        {
                            /* System.out.println("[REP] Limita de conexiuni depasita pentru domeniul \"" + domain +
                                            "\" (1 / secunda)"); */
                            Q.addLast(L);
                            continue;
                        }

                        // actualizam momentul accesarii din nou a domeniului
                        requestTimesPerDomain.put(domain, System.nanoTime());
                    }

                    // preluam lista de reguli
                    String robotsRules = robots.get(domain);

                    if (robotsRules != null) // daca exista reguli, tinem cont de ele, conform pseudo-protocolului REP
                    {
                        // IMPORTANT! Verificam daca avem voie sa exploram URL-ul curent
                        if (!RobotsParser.isAllowed(currentURL, robotsRules))
                        {
                            System.out.println("[REP] URL interzis pentru explorare: " + L);
                            continue;
                        }
                    }

                    // -------------------------------------------------------------------------------------------------

                    String P = httpClient.getResource(path, domain, port, 0);
                    if (P != null)
                    {
                        // System.out.println("[" + httpClient.getLastStatus() + "] URL procesat. Resursa salvata la: " + P);

                        // ----------------------------------- PARSE -----------------------------------
                        WebsiteInfo info = new WebsiteInfo(P, "http://" + domain + ":" + port + path);

                        // -------- REP LA NIVEL DE PAGINA -------
                        boolean linkExtractionAllowed = true;
                        String pageRobots = info.getRobots();
                        if (!pageRobots.equals("")) // daca exista mentiuni cu privire la robotii ce acceseaza pagina curenta
                        {
                            // tinem cont de ele - daca gasim "noindex" sau "nofollow", ignoram pagina
                            pageRobots = pageRobots.toLowerCase();
                            if (pageRobots.contains("noindex"))
                            {
                                System.out.println("[REP] URL interzis pentru indexare: " + L);
                                continue;
                            }
                            if (pageRobots.contains("nofollow"))
                            {
                                System.out.println("[REP] URL interzis pentru extragerea de legaturi: " + L);
                                linkExtractionAllowed = false;
                            }
                        }
                        // ---------------------------------------

                        ++numberOfPagesCrawled;

                        // extragem textul din pagina si il stocam in fisier
                        // generam numere fisierului text corespunzator, cu extensia txt
                        StringBuilder textFileNameBuilder = new StringBuilder(P);

                        // fisierele HTML ce contin "?" in nume vor primi extensia ".txt" alaturi de intregul nume
                        if (textFileNameBuilder.indexOf("?") != -1)
                        {
                            textFileNameBuilder.append(".txt");
                        }
                        else // daca nu, inlocuim extensia de dupa "." cu "txt"
                        {
                            textFileNameBuilder.replace(textFileNameBuilder.lastIndexOf(".") + 1, textFileNameBuilder.length(), "txt");
                        }
                        String textFileName = textFileNameBuilder.toString();

                        // scriem rezultatul in fisierul text
                        FileWriter fw = new FileWriter(new File(textFileName), false);
                        fw.write(info.getText());
                        fw.close();
                        // System.out.println("[FISIER_TEXT] Salvat continut text in fisierul: " + textFileName);
                        // -----------------------------------------------------------------------------

                        // marcam pagina curenta ca fiind vizitata
                        visited.add(L);

                        if (linkExtractionAllowed)
                        {
                            // extrage din P o lista noua de legaturi N
                            Set<String> N = info.getLinks();

                            // adauga N la Q
                            for (String link : N)
                            {
                                if (!visited.contains(link)) // avem grija ca URL-ul adaugat sa nu fie vizitat deja
                                {
                                    Q.addFirst(link);
                                }
                            }
                        }

                        System.out.println("[URL_FRONTIER] (" + numberOfPagesCrawled + ") " + L);
                    }
                    else // tratare de erori HTTP
                    {
                        int fetcherHTTPState = httpClient.getLastStatus();
                        if (fetcherHTTPState == 1) // nu se poate gasi IP-ul pentru domeniul curent
                        {
                            System.out.println("[DNS_SOLVER] Se sterge domeniul \"" + domain + "\" din coada de explorare.");
                            removeDomain(domain);
                            continue;
                        }
                        if (fetcherHTTPState == 301 || fetcherHTTPState == 302) // pagina mutata temporar sau permanent
                        {
                            if (!numberOfRedirectsPerDomain.containsKey(domain)) // daca domeniul este nou
                            {
                                // adaugam in lista de redirect-uri
                                numberOfRedirectsPerDomain.put(domain, 1);
                            }
                            else // domeniu explorat anterior
                            {
                                // verificam daca nu s-a depasit numarul maxim de 3 redirectionari / domeniu
                                if (numberOfRedirectsPerDomain.get(domain) < 4)
                                {
                                    numberOfRedirectsPerDomain.put(domain, numberOfRedirectsPerDomain.get(domain) + 1);
                                }
                                else // depasire, stergem domeniul din coada de explorare
                                {
                                    System.out.println("[SPIDER_TRAP] Prea multe redirectionari pe domeniul \"" + domain + "\". Se sterge din coada de explorare.");
                                    removeDomain(domain);
                                    continue;
                                }
                            }

                            if (fetcherHTTPState == 301) // pagina mutata permanent
                            {
                                // actualizam acest lucru in URL Frontier

                            }
                        }
                        else if (fetcherHTTPState == 404) // resursa negasita
                        {
                            if (!numberOfNotFoundPerDomain.containsKey(domain)) // daca domeniul este nou
                            {
                                // adaugam in lista de resurse negasite
                                numberOfNotFoundPerDomain.put(domain, 1);
                            }
                            else // domeniu explorat anterior
                            {
                                // verificam daca nu s-a depasit numarul maxim de 3 erori 404 / domeniu
                                if (numberOfNotFoundPerDomain.get(domain) < 4)
                                {
                                    numberOfNotFoundPerDomain.put(domain, numberOfNotFoundPerDomain.get(domain) + 1);
                                }
                                else // depasire, stergem domeniul din coada de explorare
                                {
                                    System.out.println("[SPIDER_TRAP] Prea multe erori de tip 404 pe domeniul \"" + domain + "\". Se sterge din coada de explorare.");
                                    removeDomain(domain);
                                    continue;
                                }
                            }
                        }
                        else if (fetcherHTTPState == 2) // prea multe redirectionari (mai mult de 3)
                        {
                            System.out.println("[SPIDER_TRAP] Prea multe redirectionari pe domeniul \"" + domain + "\". Se sterge din coada de explorare.");
                            removeDomain(domain);
                            continue;
                        }
                    }
                } catch (UnknownHostException uhe)
                {
                    System.out.println("[CLIENT_HTTP] EROARE: Nu s-a putut stabili conexiunea la " + L);
                    continue;
                } catch (IOException ioe)
                {
                    System.out.println("[CLIENT_HTTP] EROARE: Problema socket: " + L);
                    continue;
                }
            } catch (MalformedURLException e)
            {
                System.out.println("[CLIENT_HTTP] EROARE: URL eronat: " + L);
                // e.printStackTrace();
                continue;
            }
            // -----------------------------------------------------------------------------
        }
        System.out.println("[SFARSIT] Numar de pagini procesate: " + numberOfPagesCrawled);
        System.out.println("[SFARSIT] Coada de URL-uri vida sau limita de pagini atinsa.");
        System.out.println("[VITEZA] Viteza globala a fost de " + Math.round((double)numberOfPagesCrawled * 60 /
                ((double)(System.nanoTime() - globalStartTime) / 1000000000.0) * 100.0) / 100.0 + " pagini / minut.");

        // curatam in caz ca se doreste o noua explorare
        httpClient = null;
        Q.clear();
        visited.clear();
        robots.clear();
        requestTimesPerDomain.clear();
        numberOfRedirectsPerDomain.clear();
        numberOfNotFoundPerDomain.clear();
    }

    public static void main(String args[])
    {
        String startingURL = "http://riweb.tibeica.com/crawl/";
        int pageLimit = 100;

        Crawler c = new Crawler("RIWEB_CRAWLER");
        c.Crawl(startingURL, pageLimit);
    }
}
