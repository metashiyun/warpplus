package com.scchan.warpplus;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface RegistryStore {
    List<WarpRecord> load() throws IOException;
    void save(Collection<WarpRecord> records) throws IOException;
}
