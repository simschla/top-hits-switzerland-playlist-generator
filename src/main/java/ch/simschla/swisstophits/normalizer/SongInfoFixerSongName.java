package ch.simschla.swisstophits.normalizer;

import ch.simschla.swisstophits.model.SongInfo;
import java.util.List;
import lombok.NonNull;

public class SongInfoFixerSongName implements SongInfoFixer {

    @NonNull
    private final List<String> origArtists;

    @NonNull
    private final String origSong;

    private final List<String> fixedArtists;

    private final String fixedSong;

    public SongInfoFixerSongName(
            @NonNull List<String> origArtists, @NonNull String origSong, List<String> fixedArtists, String fixedSong) {
        this.origArtists = origArtists;
        this.origSong = origSong;
        this.fixedArtists = fixedArtists;
        this.fixedSong = fixedSong;
    }

    @Override
    public SongInfo fix(@NonNull SongInfo orig) {
        if (!orig.getSong().equals(origSong) || !orig.getArtists().equals(origArtists)) {
            return orig;
        }

        return orig.withSong(fixedSong != null ? fixedSong : orig.getSong())
                .withArtists(fixedArtists != null ? fixedArtists : orig.getArtists());
    }
}
