package android.speech.tts;
import java.util.Locale;
public class Voice {
    private final Locale locale;
    private final boolean network;
    public Voice(Locale locale, boolean network) { this.locale = locale; this.network = network; }
    public Locale getLocale() { return locale; }
    public boolean isNetworkConnectionRequired() { return network; }
}
