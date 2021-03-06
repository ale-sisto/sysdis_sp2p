package polimi.distsys.sp2p.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

public class StreamCipherOutputStream extends FilterOutputStream {

	private static final int BUFFER_SIZE = 4096;
	
	private final List<ResettableCipher> ciphers;
	private final byte[] buffer;
	
	public StreamCipherOutputStream(OutputStream out, List<ResettableCipher> ciphers) {
		super(out);
		this.ciphers = ciphers;
		buffer = new byte[ BUFFER_SIZE ];
	}
	
	/**
	 * Ciphers the given input and writes it
	 * @param in
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public synchronized void write( InputStream in ) throws GeneralSecurityException, IOException{
		in = wrap( in, ciphers);
		while( true ){
			int count = in.read( buffer, 0, buffer.length );
			if( count == -1)
				break;
			out.write( buffer, 0, count );
		}
	}

	private synchronized InputStream wrap( InputStream in, List<ResettableCipher> ciphers ) throws GeneralSecurityException, IOException {
		ResettableCipher rc = ciphers.get( 0 );
		if( rc.getAlgorithm().equals("RSA") ){
			in = new RSAInputStream( in , rc );
		}else
			in = new CipherInputStream( in, rc.reset() );
		List<ResettableCipher> remaining = ciphers.subList( 1, ciphers.size() );
		if( remaining.size() > 0 )
			return wrap( in, remaining ); 
		else
			return in;
	}

	@Override
	public synchronized void write( byte[] payload ) throws IOException {
		write( payload, 0, payload.length );
	}

	@Override
	public synchronized void write( byte[] payload, int start, int len ) throws IOException {
		try {
			write( new ByteArrayInputStream( payload, start, len ) );
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Trasmette un oggetto di cui si conosce a priori la dimensione
	 * in byte (serializzato)
	 * Probabilmente inutile :D
	 * @param o
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public synchronized void write( Object o ) throws IOException, GeneralSecurityException{
		PipedOutputStream pos = new PipedOutputStream();
		PipedInputStream pis = new PipedInputStream( pos );
		ObjectOutputStream oos = new ObjectOutputStream( pos );
		oos.writeObject( o );
		oos.close();
		write( pis );
	}
	
	/**
	 * Differently from the normal usage of this function in a regular
	 * OutpuStream, here we will write the full integer, not only the 
	 * first byte.
	 */
	@Override
	public synchronized void write( int num ) throws IOException {
		byte[] repr = new byte[ Integer.SIZE / 8 ];
		for(int i=0; i<repr.length; i++)
			repr[i] = (byte) ( ( num >>> ( i * 8 ) ) & 0xFF );
		write( repr );
	}
	
	/**
	 * Since the receiving part does not know the length of the
	 * stream, we first send the int representing it, and then 
	 * the object itself.
	 * @param o
	 * @throws IOException
	 */
	public synchronized void writeVariableSize( Object o ) throws IOException{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream( baos );
		oos.writeObject( o );
		oos.close();
		write( baos.size() );
		write( baos.toByteArray() );
	}

	
	/**
	 * Since the receiving part does not know the length of the
	 * stream, we first send the int representing it, and then 
	 * the payload itself.
	 * @param payload
	 * @throws IOException
	 */
	public synchronized void writeVariableSize( byte[] payload ) throws IOException{
		writeVariableSize( payload, 0, payload.length );
	}
	
	/**
	 * Since the receiving part does not know the length of the
	 * stream, we first send the int representing it, and then 
	 * the payload itself.
	 * @param payload
	 * @throws IOException
	 */
	public synchronized void writeVariableSize( byte[] payload, int start, int len ) throws IOException{
		write( len - start );
		write( payload, start, len );
	}
	
	public void flush() throws IOException {
		out.flush();
	}
		
	public static class ResettableCipher {
		
		private final Cipher cipher;
		private final String algo;
		private final int mode;
		private final Key key;
		
		private final boolean supportMultiBlock;
		private final int inputBlockSize;
		private final int outputBlockSize;
		
		
		public ResettableCipher(String algo, int mode, Key key) throws GeneralSecurityException {
			this.mode = mode;
			this.algo = algo;
			this.key = key;
			
			cipher = Cipher.getInstance(algo);
			cipher.init(mode, key);
			
			int tempSize = cipher.getBlockSize();
			if(tempSize == 0){
				supportMultiBlock = false;
				tempSize = cipher.getOutputSize(1);
				if(mode == Cipher.ENCRYPT_MODE){
					outputBlockSize = tempSize;
					inputBlockSize = tempSize -11;
				}else{
					outputBlockSize = tempSize -11;
					inputBlockSize = tempSize;
				}
			}else{
				supportMultiBlock = true;
				outputBlockSize = inputBlockSize = tempSize;
			}

		}
		
		public Cipher reset() throws GeneralSecurityException{
			Cipher cipher = Cipher.getInstance(algo);
			cipher.init(mode, key);
			return cipher;
		}

		public int getMode() {
			return mode;
		}

		public String getAlgorithm() {
			return algo;
		}

		public boolean supportsMultiBlock() {
			return supportMultiBlock;
		}

		public int getInputBlockSize() {
			return inputBlockSize;
		}

		public int getOutputBlockSize() {
			return outputBlockSize;
		}
		
		public int getOutputSize( int inputLen ){
			int blocks = (int) Math.ceil( 1.0 * inputLen / inputBlockSize );
			return outputBlockSize * blocks;
		}
		
		public int getInputSize( int outSize ){
			int blocks = (int) Math.ceil( 1.0 * outSize / outputBlockSize );
			return inputBlockSize * blocks;
		}

	}
	
	public static class RSAInputStream extends FilterInputStream {

		private final ResettableCipher cipher;
		private final byte[] out_buffer;
		private boolean available;
		private int out_pos;
		
		public RSAInputStream( InputStream in, ResettableCipher rc ) {
			super(in);
			cipher = rc;
			out_buffer = new byte[ Math.max( cipher.getOutputBlockSize(), cipher.getInputBlockSize() ) ];
			out_pos = 0;
			available = false;
		}
		
		private void readBlock() throws IOException, GeneralSecurityException{
			available = false;
			byte[] buffer = new byte[ cipher.getInputBlockSize() ];
			int pos = 0;
			while( pos < buffer.length ){
				int read = in.read( buffer, pos, buffer.length - pos );
				if( read == -1 )
					break;
				pos += read;
			}
			if( pos > 0 ){
				Cipher c = cipher.reset();
				c.update( buffer, 0, pos, out_buffer );
				c.doFinal( out_buffer, 0 );
				out_pos = 0;
				available = true;
			}
		}
		
		@Override
		public int read() throws IOException {
			if( !available || out_pos == cipher.getOutputBlockSize() ){
				try {
					readBlock();
				} catch (GeneralSecurityException e) {
					throw new IOException(e);
				}
				if(!available)
					return -1;
			}
			return out_buffer[ out_pos++ ];
		}
		
		@Override
		public int read( byte[] buf ) throws IOException {
			return read( buf, 0, buf.length );
		}
		
		@Override
		public int read( byte[] buf, int start, int len ) throws IOException {
			if( !available || out_pos == cipher.getOutputBlockSize() ){
				try {
					readBlock();
				} catch (GeneralSecurityException e) {
					throw new IOException(e);
				}
				if(!available)
					return -1;
			}
			int toCopy = Math.min( cipher.getOutputBlockSize() - out_pos, len );
			System.arraycopy( out_buffer, out_pos, buf, start, toCopy);
			out_pos += toCopy;
			return toCopy;
		}
		
	}
	
}
