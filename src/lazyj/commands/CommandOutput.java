package lazyj.commands;
import java.io.BufferedReader;
import java.io.StringReader;

/**
 * Wrapper arroung the output of an AliEn command
 */
public class CommandOutput {
    /**
     * stdout
     */
    public final String stdout;

    /**
     * stderr
     */
    public final String stderr;

    /**
     * Package protected constructor, only AliEnPool should execute commands
     *
     * @param _stdout stdout
     * @param _stderr stderr
     */
    public CommandOutput(final String _stdout, final String _stderr){
        this.stdout = _stdout;
        this.stderr = _stderr;
    }

    /**
     * Read the stdout content
     *
     * @return stdout reader
     */
    public BufferedReader reader(){
        return new BufferedReader(new StringReader(this.stdout));
    }

    /**
     * Read the stderr content
     *
     * @return stderr reader
     */
    public BufferedReader readerStderr(){
        return new BufferedReader(new StringReader(this.stderr));
    }

    /**
     * Get the number of lines in stdout
     *
     * @return number of lines
     */
    public int linesCount(){
        return countLines(this.stdout);
    }

    /**
     * Get the number of lines in stderr
     *
     * @return number of lines
     */
    public int linesCountStderr(){
        return countLines(this.stderr);
    }

    /**
     * Get the number of lines in ar arbitrary text
     *
     * @param s text
     * @return number of lines
     */
    public static int countLines(final String s){
        return countOccurrences(s, '\n') + ((s.length()>0 && s.charAt(s.length()-1)=='\n') ? 0 : 1);
    }

    /**
     * How many times one particular character shows up in a text ?
     *
     * @param s the text
     * @param c the character
     * @return number of occurences
     */
    public static int countOccurrences(final String s, final char c){
        if (s==null || s.length()==0)
            return 0;

        final char[] chars = s.toCharArray();

        int count = 0;

        for (int i=chars.length-1; i>=0; i--){
            if (chars[i]==c)
                count++;
        }

        return count;
    }

    @SuppressWarnings("nls")
	@Override
    public String toString(){
        return "STDOUT:\n---------\n"+this.stdout+"\n\nSTDERR:\n-----------\n"+this.stderr;
    }
}
