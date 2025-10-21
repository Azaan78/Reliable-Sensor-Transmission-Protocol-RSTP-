/*
 * Replace the following string of 0s with your student number
 * c4029726
 */
import java.io.File;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.File;
//Added imports
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.ArrayList;



public class Protocol {

	static final String  NORMAL_MODE="nm"   ;         // normal transfer mode: (for Part 1 and 2)
	static final String	 TIMEOUT_MODE ="wt"  ;        // timeout transfer mode: (for Part 3)
	static final String	 LOST_MODE ="wl"  ;           // lost Ack transfer mode: (for Part 4)
	static final int DEFAULT_TIMEOUT =1000  ;         // default timeout in milliseconds (for Part 3)
	static final int DEFAULT_RETRIES =4  ;            // default number of consecutive retries (for Part 3)
	public static final int MAX_Segment_SIZE = 4096;  //the max segment size that can be used when creating the received packet's buffer

	/*
	 * The following attributes control the execution of the transfer protocol and provide access to the 
	 * resources needed for the transfer 
	 * 
	 */ 

	private InetAddress ipAddress;      // the address of the server to transfer to. This should be a well-formed IP address.
	private int portNumber; 		    // the  port the server is listening on
	private DatagramSocket socket;      // the socket that the client binds to

	private File inputFile;            // the client-side CSV file that has the readings to transfer  
	private String outputFileName ;    // the name of the output file to create on the server to store the readings
	private int maxPatchSize;		   // the patch size - no of readings to be sent in the payload of a single Data segment

	private Segment dataSeg   ;        // the protocol Data segment for sending Data segments (with payload read from the csv file) to the server 
	private Segment ackSeg  ;          // the protocol Ack segment for receiving ACK segments from the server

	private int timeout;              // the timeout in milliseconds to use for the protocol with timeout (for Part 3)
	private int maxRetries;           // the maximum number of consecutive retries (retransmissions) to allow before exiting the client (for Part 3)(This is per segment)
	private int currRetry;            // the current number of consecutive retries (retransmissions) following an Ack loss (for Part 3)(This is per segment)

	private int fileTotalReadings;    // number of all readings in the csv file
	private int sentReadings;         // number of readings successfully sent and acknowledged
	private int totalSegments;        // total segments that the client sent to the server

	// Shared Protocol instance so Client and Server access and operate on the same values for the protocol’s attributes (the above attributes).
	public static Protocol instance = new Protocol();

	/**************************************************************************************************************************************
	 **************************************************************************************************************************************
	 * For this assignment, you have to implement the following methods:
	 *		sendMetadata()
	 *      readandSend()
	 *      receiveAck()
	 *      startTimeoutWithRetransmission()
	 *		receiveWithAckLoss()
	 * Do not change any method signatures, and do not change any other methods or code provided.
	 ***************************************************************************************************************************************
	 **************************************************************************************************************************************/
	/* 
	 * This method sends protocol metadata to the server.
	 * See coursework specification for full details.	
	 */
	public void sendMetadata() {
        //Sets variable to read file to null (nothing)
        Scanner Reading = null;
        //Tries if a file is read
		try{
            //Reads input file
            Reading = new Scanner(inputFile);
            //Loops until there are no next lines and counts current amout of lines (count starts from 1 not 0)
            while (Reading.hasNextLine()) {
                fileTotalReadings++;
                Reading.nextLine();
            }
        }
        //Error returned if no file found
        catch(FileNotFoundException e){
            //Message printed in terminal and error message printed too
            System.out.println("CLIENT ERROR: Error reading metadata file");
            e.printStackTrace();
            return;
        }
        //Concatenating payload string and instantiating segment object
        String payload = (fileTotalReadings + "," + outputFileName + "," + maxPatchSize);
        Segment MetaSeg = new Segment(0,SegmentType.Meta,payload,payload.length());
        System.out.println("Sending Metadata Request");

        //Converts payload of metadata segment to bytes and converts those bytes into a packet to be sent
        byte[] DataSend = MetaSeg.getPayLoad().getBytes();
        java.net.DatagramPacket PacketSend = new java.net.DatagramPacket(
                DataSend,
                DataSend.length,
                ipAddress,
                portNumber
        );
        //Tries if the packet can be sent
        try{
            //Sends packet to socket and returns message in terminal
            socket.send(PacketSend);
            System.out.println("CLIENT: Meta data segment sent to server");
        }
        //If packet cannot be found or sent message returned
        catch(java.io.IOException e){
            System.out.println("CLIENT ERROR: Failed to send meta data");
            e.printStackTrace();
        }
        //If something still saved in Reading variable then the variable is closed as not needed
        finally{
            if(Reading!=null){
                Reading.close();
            }
        }
        //MetaSegment();
        }


	/* 
	 * This method read and send the next data segment (dataSeg) to the server. 
	 * See coursework specification for full details.
	 */
	public void readAndSend() {
        try{
            //Reads input file and creates list to store payload's if csv lines are bigger than patch size
            Scanner Reading = new Scanner(inputFile);
            ArrayList<String> PayLoadList = new ArrayList<String>();
            //Loops while the there is another line that hasn't been read
            while (Reading.hasNextLine()) {
                //Declares payload to save currently read lines and patchcount to count to ensure maxPatch isn't met
                String Payload = "";
                int PatchCount = 0;
                //Loops for max patch size
                while (Reading.hasNextLine() && maxPatchSize > PatchCount){
                    //Count increments by 1, reads current line and concatenates to payload
                    PatchCount++;
                    String CurLine = Reading.nextLine().trim();
                    Payload = Payload + (CurLine);
                    //If there are other records seperate with ";"
                    if (Reading.hasNextLine()) {
                        Payload = Payload + (";");
                    }
                }
                //If run out of lines or max patch met, that payload is added to list
                PayLoadList.add(Payload);
            }
            //Loops for the amount of payloads in list
            for (String i : PayLoadList){
                //Finds index of payload for segment number and instantiates segment
                int index = PayLoadList.indexOf(i);
                int length = i.length();
                Segment ReadSeg = new Segment((index + 1),SegmentType.Data,i,length);

                System.out.println("CLIENT: Preparing datapacket for segment number " + (index + 1));

                byte[] dataByte = ReadSeg.getPayLoad().getBytes();
                java.net.DatagramPacket packet = new java.net.DatagramPacket(
                        dataByte,
                        dataByte.length,
                        ipAddress,
                        portNumber
                );
                try{
                socket.send(packet);
                totalSegments++;
                System.out.println("CLIENT: Sending packet number: " + (index + 1));
                System.out.println("CLIENT: Packet length: " + packet.getLength());
                System.out.println("CLIENT: Packet payload: " + packet.getAddress());
                System.out.println("CLIENT: Packet port: " + packet.getPort());
                System.out.println("CLIENT: Packet payload: " + ReadSeg.getPayLoad());
                }
                catch(java.io.IOException e){
                    System.out.println("CLIENT ERROR: Failed to send data");
                    e.printStackTrace();
                }
            }
        }
        //If there is an issue reading the file
        catch(java.io.FileNotFoundException e){
            System.out.println("CLIENT ERROR: Error reading file");
            e.printStackTrace();
            return;
        }
    }


	/* 
	 * This method receives the current Ack segment (ackSeg) from the server 
	 * See coursework specification for full details.
	 */
	public boolean receiveAck() {
        //Prints in terminal message to let user know the current segment
        System.out.println("CLIENT: Waiting for ACK signal number " + ackSeg.getSeqNum());
        try{
            byte[]
        }
        catch{

        }
		return false;
	}

	/* 
	 * This method starts a timer and does re-transmission of the Data segment 
	 * See coursework specification for full details.
	 */
	public void startTimeoutWithRetransmission()   {  
		System.exit(0);
	}


	/* 
	 * This method is used by the server to receive the Data segment in Lost Ack mode
	 * See coursework specification for full details.
	 */
	public void receiveWithAckLoss(DatagramSocket serverSocket, float loss)  {
		System.exit(0);
	}


	/*************************************************************************************************************************************
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************
	These methods are implemented for you .. Do NOT Change them 
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************/	 
	/* 
	 * This method initialises ALL the 14 attributes needed to allow the Protocol methods to work properly
	 */
	public void initProtocol(String hostName , String portNumber, String fileName, String outputFileName, String batchSize) throws UnknownHostException, SocketException {
		instance.ipAddress = InetAddress.getByName(hostName);
		instance.portNumber = Integer.parseInt(portNumber);
		instance.socket = new DatagramSocket();

		instance.inputFile = checkFile(fileName); //check if the CSV file does exist
		instance.outputFileName =  outputFileName;
		instance.maxPatchSize= Integer.parseInt(batchSize);

		instance.dataSeg = new Segment(); //initialise the data segment for sending readings to the server
		instance.ackSeg = new Segment();  //initialise the ack segment for receiving Acks from the server

		instance.fileTotalReadings = 0; 
		instance.sentReadings=0;
		instance.totalSegments =0;

		instance.timeout = DEFAULT_TIMEOUT;
		instance.maxRetries = DEFAULT_RETRIES;
		instance.currRetry = 0;		 
	}


	/* 
	 * check if the csv file does exist before sending it 
	 */
	private static File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("CLIENT: File does not exists"); 
			System.out.println("CLIENT: Exit .."); 
			System.exit(0);
		}
		return file;
	}

	/* 
	 * returns true with the given probability to simulate network errors (Ack loss)(for Part 4)
	 */
	private static Boolean isLost(float prob) 
	{ 
		double randomValue = Math.random();  //0.0 to 99.9
		return randomValue <= prob;
	}

	/* 
	 * getter and setter methods	 *
	 */
	public String getOutputFileName() {
		return outputFileName;
	} 

	public void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	} 

	public int getMaxPatchSize() {
		return maxPatchSize;
	} 

	public void setMaxPatchSize(int maxPatchSize) {
		this.maxPatchSize = maxPatchSize;
	} 

	public int getFileTotalReadings() {
		return fileTotalReadings;
	} 

	public void setFileTotalReadings(int fileTotalReadings) {
		this.fileTotalReadings = fileTotalReadings;
	}

	public void setDataSeg(Segment dataSeg) {
		this.dataSeg = dataSeg;
	}

	public void setAckSeg(Segment ackSeg) {
		this.ackSeg = ackSeg;
	}

	public void setCurrRetry(int currRetry) {
		this.currRetry = currRetry;
	}

}
