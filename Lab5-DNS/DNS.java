import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DNS {
	
    private int port;
    
    private String server_dns;

    public DNS(String server, int port)
    {
        server_dns = server;
        this.port = port;
    }
    // rezolva o cerere DNS pentru domeniul specificat
    void rezolva_cerere_dns(String domain) throws SocketException, UnknownHostException, IOException
    {
        byte[] requestByteArray = new byte[12 + domain.length() + 6];         // iau un array de octeti de dimensiune 12 + strlen() + 6
        Random r = new Random();         // setez octetii care trebuie pentru cerere         // Identifier
        int identifier = r.nextInt(1 << 16 - 1); // intreg pe doi octeti
        byte[] identifierBytes = ByteBuffer.allocate(4).putInt(identifier).array();         // luam MSB si LSB din ultimii 2 octeti din int
        byte MSB = identifierBytes[2];
        byte LSB = identifierBytes[3];
        requestByteArray[0] = MSB;
        requestByteArray[1] = LSB;
        requestByteArray[2] = 0x01;         // Recursion Desired
        requestByteArray[5] = 0x1;         // Question Count -> 1 intrebare
        int questionIndex = 12;         // de la octetul 12 incepe Question Name
        byte questionBuffer[] = new byte[domain.length()];
        int k = 0; // indicele din buffer-ul temporar
        char[] domainNameChars = domain.toCharArray();
        for (int j = 0; j < domain.length(); ++j) // parsam domeniul si il stocam in formatul specific
        {
            if (domainNameChars[j] != '.')
            {
                questionBuffer[k++] = (byte)domainNameChars[j];
            }
            else // am ajuns la punct
            {
                requestByteArray[questionIndex++] = (byte)k;                 // setam numarul de caractere
                for (int i = 0; i < k; ++i)                 // punem toate caracterele de dinaintea punctului curent
                {
                    requestByteArray[questionIndex++] = questionBuffer[i];
                }
                k = 0;                 // resetam buffer-ul pentru urmatorul punct
            }
            if (j == domain.length() - 1)             // daca am ajuns la final, punem si ultimele caractere
            {
                requestByteArray[questionIndex++] = (byte)k;                 // setam numarul de caractere
                for (int i = 0; i < k; ++i)                 // punem toate caracterele de dinaintea punctului curent
                {
                    requestByteArray[questionIndex++] = questionBuffer[i];
                }
            }
        }
        requestByteArray[questionIndex] = 0x0;         // setam terminatorul de nume
        requestByteArray[questionIndex + 2] = 0x1;         // Question Type -> 1 = adresa IP
        requestByteArray[questionIndex + 4] = 0x1;         // Question Class -> 1 = clasa internet
        DatagramSocket datagramSocket = new DatagramSocket();         // construim un datagram cu destinatarul IP-ul serverului DNS, portul 53
        try {
            datagramSocket.setSoTimeout(3000); // setam un timeout de 3 secunde
            InetAddress IP = InetAddress.getByName(server_dns);
            DatagramPacket requestPacket = new DatagramPacket(requestByteArray, requestByteArray.length, IP, port);
            System.out.println("S-a  facut pachetul de cerere DNS pt domeniul \"" + domain + "\".");
            for (int i = 0; i < requestByteArray.length; ++i) {
                System.out.print('\t');
                System.out.printf("[0x%02X] ", requestByteArray[i]);
                if ((i + 1) % 9 == 0) {
                    System.out.println();
                }
            }
            datagramSocket.send(requestPacket);             // trimitem pachetul la server-ul DNS
            System.out.println("Pachetul a fost trimis catre server-ul " + server_dns + ".");
            byte[] responseByteBuffer = new byte[512];             // preluam raspunsul de la server
            DatagramPacket responsePacket = new DatagramPacket(responseByteBuffer, 512);             // il punem intr-un buffer de 512 octeti
            System.out.println("Se asteapta raspuns de la server...");
            datagramSocket.receive(responsePacket);
            System.out.println("Pachetul de raspuns a fost primit cu succes:");
            for (int i = 0; i < responseByteBuffer.length; ++i) {
                System.out.print('\t');
                System.out.printf("[0x%02X] ", responseByteBuffer[i]);
                if ((i + 1) % 10 == 0) {
                    System.out.println();
                }
            }
            datagramSocket.close();
                    
            LSB = responseByteBuffer[1]; // parsam raspunsul primit // verificam intai identificatorul sa se potriveasca cu cel al cererii
            MSB = responseByteBuffer[0];
            int receivedIdentifier = (((0xFF) & MSB) << 8) | (0xFF & LSB);

            if (identifier == receivedIdentifier) {
                System.out.println("Identificatorii se potrivesc: " + receivedIdentifier);
            }

            if ((responseByteBuffer[3] & 0x0F) == 0x00) // ultimii 4 biti sunt codul de raspuns
            {
                System.out.println("Nicio eroare produsa: RCode 0 -> OK");
            } else {
                int errorCode = responseByteBuffer[3] & 0x0F;
                System.out.println("S-a produs o eroare: RCode = " + errorCode);
            }        
            LSB = responseByteBuffer[7]; // verificam numarul de raspunsuri primite (Answer Record Count)
            MSB = responseByteBuffer[6];
            int numberOfResponses = (((0xFF) & MSB) << 8) | (0xFF & LSB);
            System.out.println("Numarul de raspunsuri primite: " + numberOfResponses);

            
            
            LSB = responseByteBuffer[9]; // verificam si alte informatii primite // Name Server Count -> informatii despre autoritati
            MSB = responseByteBuffer[8];
            int numberOfAuthorityInfos = (((0xFF) & MSB) << 8) | (0xFF & LSB);
            System.out.println("Numarul de informatii despre autoritati primite: " + numberOfAuthorityInfos);

            
            LSB = responseByteBuffer[11]; // Additional Record Count -> informatii aditionale primite
            MSB = responseByteBuffer[10];
            int numberOfAdditionalRecords = (((0xFF) & MSB) << 8) | (0xFF & LSB);
            System.out.println("Numarul de informatii aditionale primite: " + numberOfAdditionalRecords);

           
            int answerIndex = 12 + domain.length() + 6; // pentru ca server-ul mentine informatiile din cererea clientului // indicele octetului de unde incep Resource Records
            int recordIndex = 1;
            while (recordIndex <= numberOfResponses + numberOfAdditionalRecords + numberOfAuthorityInfos) {                            
                String particleName = obtinere_particula_pointer(answerIndex, responseByteBuffer); // Resource Name // preluam numele de particula recursiv
                if ((responseByteBuffer[answerIndex] & 0xFF) < 192) // dimensiune de particula               // trebuie sa sarim peste un numar de octeti dependent de tipul de particula
                {
                    answerIndex += particleName.length() + 1; //dimensiune de particula
                } else // pointer
                {
                    answerIndex += 2; // pointer-ul e pe 2 octeti // calculam indicele de octet
                }
                particleName = particleName.substring(0, particleName.length() - 1);
                System.out.print("(" + recordIndex + ") Numele de particula: " + particleName);            
                MSB = responseByteBuffer[answerIndex++]; // Record Type (2 octeti)
                LSB = responseByteBuffer[answerIndex++];
                int recordType = (((0xFF) & MSB) << 8) | (0xFF & LSB);
                System.out.print(" | Record Type: " + recordType);
                if (recordType == 1) {
                    System.out.print(" (adresa IPv4)");
                } else if (recordType == 2) {
                    System.out.print(" (server de nume)");
                } else if (recordType == 5) {
                    System.out.print(" (nume canonic)");
                } else if (recordType == 28) {
                    System.out.print(" (adresa IPv6)");
                }                
                MSB = responseByteBuffer[answerIndex++]; // Record Class (2 octeti)
                LSB = responseByteBuffer[answerIndex++];
                int recordClass = (((0xFF) & MSB) << 8) | (0xFF & LSB);
                System.out.print(" | Record Class: " + recordClass);
                if (recordClass == 1) {
                    System.out.print(" (internet)");
                }

                // TTL (Time To Live) - 4 octeti
                byte b3 = responseByteBuffer[answerIndex++];
                byte b2 = responseByteBuffer[answerIndex++];
                byte b1 = responseByteBuffer[answerIndex++];
                byte b0 = responseByteBuffer[answerIndex++];
                int TTL = ((0xFF & b3) << 24) | ((0xFF & b2) << 16) | ((0xFF & b1) << 8) | (0xFF & b0);
                long hours = TimeUnit.MILLISECONDS.toHours(TTL);
                TTL -= TimeUnit.HOURS.toMillis(hours);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(TTL);
                TTL -= TimeUnit.MINUTES.toMillis(minutes);
                long seconds = TimeUnit.MILLISECONDS.toSeconds(TTL);
                System.out.print(" | TTL: " + hours + "h " + minutes + "m " + seconds + "s");

                // Record Data Length (2 octeti)
                MSB = responseByteBuffer[answerIndex++];
                LSB = responseByteBuffer[answerIndex++];
                int dataLength = (((0xFF) & MSB) << 8) | (0xFF & LSB);

                if (dataLength == 4 && recordType == 1) // daca Data Length = 4, si Record Type = 1, raspunsul contine adresa IPv4
                {
                    // construim adresa IPv4
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dataLength; ++i) {
                        sb.append(responseByteBuffer[answerIndex++] & 0xFF);
                        sb.append('.');
                    }
                    sb.deleteCharAt(sb.length() - 1); // stergem ultimul punct, pus in plus
                    System.out.println(" | Adresa IPv4: " + sb.toString());
                } else if (dataLength == 16 && recordType == 28) // daca Data Length = 16 si Record Type = 28, raspunsul contine adresa IPv6
                {
                    // construim adresa IPv6
                    String ipv6 = String.format("%02X%02X:%02X%02X:%02X%02X:%02X%02X:%02X%02X:%02X%02X:%02X%02X:%02X%02X",
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF,
                            responseByteBuffer[answerIndex++] & 0xFF);

                    System.out.println(" | Adresa IPv6: " + ipv6);
                } else if (recordType == 2) // server de nume
                {
                    // preluam numele de particula recursiv
                    String nsName = obtinere_particula_pointer(answerIndex, responseByteBuffer);
                    // System.out.println(responseByteBuffer[answerIndex] & 0xFF);

                    // sarim peste dataLength octeti
                    answerIndex += dataLength;

                    nsName = nsName.substring(0, nsName.length() - 1);
                    System.out.println(" | Server de nume: " + nsName);
                } else if (recordType == 5) // nume canonic
                {
                    // preluam numele de particula recursiv
                    String canonicalName = obtinere_particula_pointer(answerIndex, responseByteBuffer);
                    // System.out.println(responseByteBuffer[answerIndex] & 0xFF);

                    // sarim peste dataLength octeti
                    answerIndex += dataLength;

                    canonicalName = canonicalName.substring(0, canonicalName.length() - 1);
                    System.out.println(" | Nume canonic: " + canonicalName);
                } else // altfel, afisam datele asa cum sunt
                {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dataLength; ++i) {
                        sb.append((char) (responseByteBuffer[answerIndex++] & 0xFF));
                    }
                    System.out.println(" | " + sb.toString());
                }
                ++recordIndex;
            }
        }
        catch (SocketTimeoutException soe)
        {
            System.out.println("Eroare: Server-ul DNS nu a raspuns la timp.");
            return;
        }
    }
    
    
    
    
    
    private String obtinere_particula_pointer(int pointerIndex, byte[] buffer)     // functie care preia o particula de nume folosind un pointer sau o dimensiune de particula trimisa ca index
    {     // functia este recursiva, pentru ca putem avea pointeri la alti pointeri
        if ((buffer[pointerIndex] & 0xFF) == 0x0) // cat timp nu am ajuns la octetul terminator de nume
        {
            return "";
        }
        if ((buffer[pointerIndex] & 0xFF) >= 192) // iar am gasit pointer
        {
            int newPointerIndex = ((buffer[pointerIndex] & 0x3F) << 8) | (buffer[pointerIndex + 1] & 0xFF);             // calculam indicele de octet
            return obtinere_particula_pointer(newPointerIndex, buffer);
        }
        int currentNumberOfCharacters = buffer[pointerIndex++] & 0xFF;         // am ajuns pe dimensiune de particula, atunci construim sirul de caractere
        StringBuilder currentElement = new StringBuilder();
        for (int i = 0; i < currentNumberOfCharacters; ++i) // preluam cate o parte de particula
        {
            currentElement.append((char)(buffer[pointerIndex + i] & 0xFF));
        }
        pointerIndex += currentNumberOfCharacters;         // trecem la elementul urmator (daca exista)
        return (currentElement.toString() + "." + obtinere_particula_pointer(pointerIndex, buffer));
    }
    
    
    
    
    public static void main(String args[])  
    {      
        String nsTuiasi = "81.180.223.1";  // servere de nume
        String tuiasi = "www.tuiasi.ro";        // site-uri de test
        DNS dns = new DNS(nsTuiasi, 53);
 //       dns.rezolva_cerere_dns(tuiasi);    
    }

}
