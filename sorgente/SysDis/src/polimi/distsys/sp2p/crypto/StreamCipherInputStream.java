package polimi.distsys.sp2p.crypto;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.CipherInputStream;

import polimi.distsys.sp2p.crypto.StreamCipherOutputStream.ResettableCipher;

public class StreamCipherInputStream extends FilterInputStream {
	
	private final List<ResettableCipher> ciphers;
	private final DigestInputStream digestStream;
	
	public StreamCipherInputStream(InputStream out, List<ResettableCipher> ciphers) throws NoSuchAlgorithmException {
		super(out);
		this.ciphers = ciphers;
		digestStream = new DigestInputStream();
	}
	
	public synchronized InputStream readFixedSize( int len ) throws GeneralSecurityException{
		//Determine ciphered size
		List<ResettableCipher> reversed = new ArrayList<ResettableCipher>( ciphers );
		Collections.reverse( reversed );
		for( ResettableCipher rc : reversed ){
			len = rc.getInputSize( len );
		}
		return readFixedRawSize( len );
	}
	
	public synchronized InputStream readFixedRawSize( int len ) throws GeneralSecurityException{
		InputStream in = new LimitedInputStream( this.in, len );
		for( ResettableCipher rc : ciphers ){
			if( rc.getAlgorithm().equals("RSA") )
				in = new StreamCipherOutputStream.RSAInputStream( in, rc ); 
			else
				in = new CipherInputStream( in, rc.reset() ); 
		}
		digestStream.setInput( in );
		return digestStream;
	}
	
	@Override
	public synchronized int read() throws IOException {
		try {
			return readFixedSize( 1 ).read();
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}
	
	public synchronized int readInt() throws GeneralSecurityException, IOException{
		byte[] repr = readFixedSizeAsByteArray( Integer.SIZE / 8 );
		int ret = 0;
		for( int i=0; i<repr.length; i++)
			ret += (repr[i] & 0xFF) << ( i * 8 ) ;
		return ret;
	}
	
	
	public synchronized <E extends Enum<?>>E readEnum( Class<E> type ) throws GeneralSecurityException, IOException{
		int value = readInt();
		try{
			return type.getEnumConstants()[ value ];
		}catch(ArrayIndexOutOfBoundsException e){
			throw new IOException( e );
		}
	}

	public synchronized byte[] readFixedSizeAsByteArray(int len) throws GeneralSecurityException, IOException{
		return readFixedSizeAsByteArray(len, true);
	}
	
	public synchronized byte[] readFixedSizeAsByteArray(int len, boolean postCheck) throws GeneralSecurityException, IOException{
		InputStream in = readFixedSize( len );
		if(postCheck){
			byte[] ret = new byte[ len ];
			int received = 0;
			while( received < len ){
				int count = in.read( ret, received, len-received );
				if( count == -1)
					throw new IOException( "end of stream reached too early" );
				received += count;
			}
			return ret; 
		}else{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[512];
			int recv;
			while( (recv = in.read(buf, 0, buf.length)) != -1)
				baos.write(buf, 0, recv);
			return baos.toByteArray();
		}
	}
	
	public synchronized <E>E readObject( Class<E> type, int len ) throws IOException, GeneralSecurityException, ClassNotFoundException{
		ObjectInputStream ois = new ObjectInputStream( readFixedRawSize( len ) );
		Object o = ois.readObject();
        ois.close();
        return type.cast(o);
	}
	
	public synchronized Object readObject( int len ) throws IOException, GeneralSecurityException, ClassNotFoundException{
		ObjectInputStream ois = new ObjectInputStream( readFixedRawSize( len ) );
		Object o = ois.readObject();
        ois.close();
        return o;
	}

	public synchronized <E>E readObject( Class<E> type ) throws IOException, GeneralSecurityException, ClassNotFoundException{
		int size = readInt();
		return readObject( type, size );
	}
	
	public synchronized Object readObject() throws IOException, GeneralSecurityException, ClassNotFoundException{
		int size = readInt();
		return readObject( size );
	}
	
	public synchronized byte[] readVariableSize() throws GeneralSecurityException, IOException{
		int size = readInt();
		return readFixedSizeAsByteArray( size );
	}
	
	public void checkDigest() throws GeneralSecurityException, IOException{
		byte[] my_digest = digestStream.getDigest();
		byte[] his_digest = readFixedSizeAsByteArray( my_digest.length );
		if( ! Arrays.equals( my_digest, his_digest) )
			throw new GeneralSecurityException( "Incorrect digest" );
	}
	
	public void activateDigest(){
		digestStream.activate();
	}
	
	public void deactivateDigest(){
		digestStream.deactivate();
	}
	
	public static class LimitedInputStream extends FilterInputStream {

		private int remaining;
		
		public LimitedInputStream(InputStream in, int len) {
			super(in);
			remaining = len;
		}
		
		@Override
		public synchronized int read() throws IOException {
			if( remaining <= 0)
				return -1;
			int ret = in.read();
			if(ret != -1)
				remaining--;
			return ret;
		}
		
		@Override
		public synchronized int read( byte[] buf ) throws IOException {
			return read( buf, 0, buf.length );
		}
		
		@Override
		public synchronized int read( byte[] buf, int start, int toRead) throws IOException {
			if( remaining <= 0)
				return -1;
			int max = Math.min( Math.min(remaining, toRead), buf.length - start );
			int count = in.read( buf, start, max);
			if(count >= 0)
				remaining -= count;
			return count;
		}
		
		@Override
		public synchronized void close(){
			// Do nothing
		}
		
	}

}
