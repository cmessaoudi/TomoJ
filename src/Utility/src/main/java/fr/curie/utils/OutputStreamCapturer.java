package fr.curie.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

/**
 * Created by cmessaoudi on 05/12/2017.
 */
public class OutputStreamCapturer {
    PrintStream oldOut;

    private ByteArrayOutputStream baos;
    private PrintStream previous;
    private boolean capturing;

    public OutputStreamCapturer(){
        start();
    }

    public void start() {
        if (capturing) {
            return;
        }

        capturing = true;
        previous = System.out;
        baos = new ByteArrayOutputStream();

        OutputStream outputStreamCombiner =
                new MyOutputStream(Arrays.asList(previous, baos));
        PrintStream custom = new PrintStream(outputStreamCombiner);

        System.setOut(custom);
    }

    public String stop() {
        if (!capturing) {
            return "";
        }

        System.setOut(previous);

        String capturedValue = baos.toString();

        baos = null;
        previous = null;
        capturing = false;

        return capturedValue;
    }




    private class MyOutputStream extends OutputStream {
        private List<OutputStream> outputStreams;

        public MyOutputStream(List<OutputStream> outputStreams) {
            this.outputStreams = outputStreams;
        }

        public void write(int b) throws IOException {
            for (OutputStream os : outputStreams) {
                os.write(b);
            }
        }

        public void flush() throws IOException {
            for (OutputStream os : outputStreams) {
                os.flush();
            }
        }

        public void close() throws IOException {
            for (OutputStream os : outputStreams) {
                os.close();
            }
        }
    }
}
