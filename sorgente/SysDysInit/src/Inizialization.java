import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Random;
import java.util.Scanner;


public class Inizialization {
	
	private static final int ASYMM_KEY_SIZE = 1024;
	private static final String ASYMM_ALGO = "RSA";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if( args.length != 1) {
			System.exit(-1);
		}
		if(!(args[0].equals("supernode")||args[0].equals("simplenode"))) {
			System.out.println("Gli argomenti ammissibili sono: supernode, simplenode");
			System.exit(-1);
		}
		
		if(args[0].equals("simplenode")) {
			
			initializeNodeFile(args[0],"simplenode.info");
			
			
		}
		
		if(args[0].equals("supernode")) {
			
			System.out.println("Inserisci la porta:");
			Scanner sc = new Scanner(System.in);
			
			
			int port = Integer.valueOf(sc.nextLine());
			initializeNodeFile(args[0], "supernode.info", port);
		}
		
	}

	
	
	/**
	 * creo il file con le informazioni del nodo
	 * 
	 * struttura file: 
	 * < private key > : < public key > : < porta >   
	 * 
	 * @param fileName
	 * @param port
	 */
	private static void initializeNodeFile(String dirName,String fileName, int port) {

		try {
			
			Random rnd = new Random();
	
			//crea la directory
			String directoryName = dirName + String.valueOf(rnd.nextInt(4096));
			
			File directory = new File(directoryName);
			directory.mkdir();
			
			//genera le chiavi
			KeyPair kp = keygen();
			PrivateKey priv = kp.getPrivate();
			PublicKey pub = kp.getPublic();
			
			//apre il file come stream
			FileOutputStream fos = new FileOutputStream(directory.getAbsolutePath() + File.separator + fileName);
			
			//crea la stringa da appendere
			StringBuilder sb = new StringBuilder();
			sb.append(Serializer.base64Encode(pub.getEncoded()));
			sb.append(":");
			sb.append( Serializer.base64Encode(priv.getEncoded()));
					
			
			if( port != -1) {
				
				sb.append(":");
				sb.append(
						port
						);
			}
			
			fos.write(sb.toString().getBytes());
			fos.close();
			
			// caso nodo semplice
			if(port == -1) {
				
				//aggiungo la pub key al file delle credenziali
				FileOutputStream credentialStream = new FileOutputStream("credentials.list", true);
				StringBuilder credentialString = new StringBuilder();
				credentialString.append(Serializer.base64Encode(pub.getEncoded()));
						
				credentialString.append("\n");
				
				credentialStream.write(credentialString.toString().getBytes());
				credentialStream.close();
				
			}
			else
			{
				//aggiungo il supernodo alla lista
				
				//aggiungo la pub key al file delle credenziali
				FileOutputStream superNodeStream = new FileOutputStream("supernodes.list", true);
				StringBuilder sBuilder = new StringBuilder();
				sBuilder.append("<host>:"+ port + ":");
				sBuilder.append(Serializer.base64Encode(pub.getEncoded()));
						
				sBuilder.append("\n");
				
				superNodeStream.write(sBuilder.toString().getBytes());
				superNodeStream.close();
			}
			
		}

		catch(Exception e) { }


	}
	
	// inizializza il file senza la porta
	private static void initializeNodeFile(String dirName,String fileName) {
		initializeNodeFile(dirName, fileName, -1);
	}
	
	
	
	//KEY GENERATION
	
		/**
		 * Genera la coppia di chiave pubbliche private dei nodi
		 * per essere utilizzate con RSA
		 * 
		 * @return
		 * @throws NoSuchAlgorithmException
		 */
		private static final KeyPair keygen() throws NoSuchAlgorithmException {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(ASYMM_ALGO);
			kpg.initialize(ASYMM_KEY_SIZE);
			KeyPair simplekp = kpg.genKeyPair();
			
			return simplekp;
		}
}
