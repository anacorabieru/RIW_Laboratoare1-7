package lab6httpclient;
import java.io.*;
import java.net.Socket;

public class HTTPclient {

    private String userAgent;
    private String resourceFolder;
    private String lastStatus;

    public HTTPclient(String userAgent, String resSaveFolder)
    {
        this.userAgent = userAgent;
        resourceFolder = resSaveFolder;
    }
    
    public boolean getResource(String resName, String domainName, String host, int port) throws IOException // preia o resursa web folosind protocolul HTTP 1.1
    {
        StringBuilder requestBuilder = new StringBuilder();        // construim cererea HTTP
        requestBuilder.append("GET " + resName + " HTTP/1.1\r\n");        // linia de cerere
        requestBuilder.append("Host: " + domainName + "\r\n");        // antetul Host
        requestBuilder.append("User-Agent: " + userAgent + "\r\n");        // antetul User-Agent
        requestBuilder.append("Connection: close\r\n");        // antetul Connection
        requestBuilder.append("\r\n");        // final de cerere

        String httpRequest = requestBuilder.toString();        // terminam de construit cererea, convertind in sir de caractere
        System.out.println("Cerere HTTP construita:\n");
        System.out.println(httpRequest);
        Socket tcpSocket = new Socket(host, port);        // deschidem socket-ul TCP
        DataOutputStream outToServer = new DataOutputStream(tcpSocket.getOutputStream()); // buffer de iesire (pt cerere)
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream())); // buffer de intrare (pt raspuns)
        outToServer.writeBytes(httpRequest);        // trimitem cererea
        System.out.println("\nCerere trimisa catre serverul " + host + ".");
        System.out.println("Raspuns de la server: \n");        // preluam raspunsul de la server
        String responseLine;
        boolean responseOK = false;
        // prima linie este linia de stare, ce contine codul de raspuns
        responseLine = inFromServer.readLine();
        if (responseLine.contains("200 OK"))
        {
            responseOK = true;
        }
        lastStatus = responseLine;
        System.out.println(responseLine);
        while ((responseLine = inFromServer.readLine()) != null)        // afisam si restul liniilor din antet
        {
            if (responseLine.equals("")) // sfarsit de antet -> deci urmeaza continutul raspunsului
            {
                break;
            }
            System.out.println(responseLine);
        }
        if (responseOK) // salvam continutul paginii doar daca raspunsul este 200 OK
        {
            StringBuilder pageBuilder = new StringBuilder();            // construim continutul paginii trimise de server
            while ((responseLine = inFromServer.readLine()) != null)
            {
                pageBuilder.append(responseLine + System.lineSeparator());
            }
            String htmlFilePath = resourceFolder + "/" + domainName + resName;            // construim calea de salvare a resursei
            if (!(htmlFilePath.endsWith(".html") || htmlFilePath.endsWith("htm")) && !resName.equals("/robots.txt"))
            {
                if (!htmlFilePath.endsWith("/"))
                {
                    htmlFilePath += "/";
                }
                htmlFilePath += "index.html";
            }
            File file = new File(htmlFilePath);
            File parentDirectory = file.getParentFile();
            if (!parentDirectory.exists())
            {
                parentDirectory.mkdirs();
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFilePath));            // salvam resursa in calea construita
            writer.write(pageBuilder.toString());
            writer.close();
            System.out.println("\nContinutul raspunsului a fost plasat in fisierul \"" + htmlFilePath + "\".");
        }
        tcpSocket.close();        // inchidem socket-ul
        return true;
    }

    public String getLastStatus()
    {
        return lastStatus;
    }

    public static void main(String args[])
    {
        HTTPclient httpClient = new HTTPclient("CLIENT RIW", "./http");
        String tibeica = "riweb.tibeica.com";
        try
        {
            httpClient.getResource("/crawl", tibeica, "67.207.88.228", 80);
        }
        catch (IOException ioe)
        {
            System.out.println("Eroare socket:");
            ioe.printStackTrace();
        }
    }
}
