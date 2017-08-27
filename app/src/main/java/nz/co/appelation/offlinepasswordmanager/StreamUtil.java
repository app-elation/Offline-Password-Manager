package nz.co.appelation.offlinepasswordmanager;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Static util class - for stream functions.
 */
public class StreamUtil {

    private static final String TAG = StreamUtil.class.getSimpleName();

    private StreamUtil(){
    }

    /**
     * Cheaper than a BufferedStreamReader... maybe.
     * Also - doesn't advance the read-marker in the underlying stream (past the bytes we're reading as the header),
     * cause we are going to need it to read the rest (serialised PasswordDB object / CSV).
     * Assumes the stream has at least numberOfBytes in it.
     * Not the most efficient method, but is only used to read the header fields - so not too bad.
     * The problem with InputStream.read(byte[] b) is that you may not get b.length number of bytes read if stream is not ready...
     * InputStream.read(byte[] b) will BLOCK until 1 byte is ready, and you deal with the stream 1 byte at a time.
     */
    public static byte[] readBytes(InputStream is, int numberOfBytes) throws IOException {
        byte[] ret = new byte[numberOfBytes];
        for (int i = 0; i < numberOfBytes; i++){
            int val = is.read();
            if (val != -1){
                ret[i] = (byte)val;
            } else {
                Log.e(TAG, "Could not read enough bytes from stream");
                throw new RuntimeException("Could not read enough bytes from stream");
            }
        }

        return ret;
    }

}
