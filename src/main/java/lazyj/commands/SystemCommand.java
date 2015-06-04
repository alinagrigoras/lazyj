package lazyj.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;


/**
 * Execute system commands
 */
public final class SystemCommand extends Thread{

    /**
     * The reader to consume
     */
    private final BufferedReader br;

    /**
     * Builder to fill
     */
    private final StringBuilder sb;

    /**
     * @param reader
     * @param builder
     */
    private SystemCommand(final BufferedReader reader, final StringBuilder builder){
        this.br = reader;
        this.sb = builder;
    }

    @Override
    public void run(){
        drain(this.br, this.sb);
    }

    /**
     * Drain the reader to the builder
     * 
     * @param br
     * @param sb
     */
    private static void drain(final BufferedReader br, final StringBuilder sb){
        try{
            String sLine;

            boolean bNext = false;

            while ( (sLine = br.readLine())!=null ){
                if (bNext)
                    sb.append('\n');

                sb.append(sLine);

                bNext = true;
            }
        }
        catch(final IOException ioe){
            // ignore
        }
    }

    /**
     * Execute the given command
     * @param command to execute
     * @return the output. or <code>null</code> if any problem
     */
    public static CommandOutput executeCommand(final List<String> command){
        return executeCommand(command, false);
    }

    /**
     * Execute the given command, optionally redirecting the stderr to stdout
     * @param command command to execute
     * @param bRedirectStderr if true then stderr will be mangled with stdout
     * @return the output, or <code>null</code> if there was a problem
     */
    public static CommandOutput executeCommand(final List<String> command, final boolean bRedirectStderr){
        final ProcessBuilder pb = new ProcessBuilder(command);

        pb.redirectErrorStream(bRedirectStderr);

        final Process p;

        try{
            p = pb.start();
        }
        catch (final IOException ioe){
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        final StringBuilder sbErr = new StringBuilder();

        final BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()), 102400);
        final BufferedReader brErr = new BufferedReader(new InputStreamReader(p.getErrorStream()), 102400);
 
        Thread t = null;
        
        if (!bRedirectStderr) {
        	t = new SystemCommand(brErr, sbErr);

        	t.start();
        }

        drain(br, sb);

        try{
        	if (t!=null)
        		t.join();
        }
        catch (final InterruptedException ie){
            // ignore
        }

        int exitCode;
        
        try{
            exitCode = p.waitFor();
        }
        catch (final Throwable e){
            // ignore
        	exitCode = -1;
        }

        return new CommandOutput(sb.toString(), sbErr.toString(), exitCode);
    }
    
    /**
     * Have the command executed via a bash interpreter 
     * 
     * @param command Full command as a string 
     * @return the output The result of the command after running
     */
    public static CommandOutput bash(final String command){
    	return bash(command, false);
    }
    
    // TODO Implement RedirectStderr 
    /**
     * Have the command executed via a bash interpreter 
     * 
     * @param command Full command to run
     * @param bRedirectStderr Redirect standard error output
     * @return the output The output of the command
     */
    @SuppressWarnings("nls")
	public static CommandOutput bash(final String command, final boolean bRedirectStderr){
    	return executeCommand(Arrays.asList("/bin/bash", "-c", command));
    }

}
