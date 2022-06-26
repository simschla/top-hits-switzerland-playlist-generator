package ch.simschla.swisstophits.normalizer;

import ch.simschla.swisstophits.model.SongInfo;
import lombok.NonNull;

public interface SongInfoFixer {

    SongInfo fix(@NonNull SongInfo orig);
}
