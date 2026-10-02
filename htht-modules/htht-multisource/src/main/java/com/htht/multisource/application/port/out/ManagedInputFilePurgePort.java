package com.htht.multisource.application.port.out;

/** Performs the filesystem part of a managed-source purge. */
public interface ManagedInputFilePurgePort {
    enum Result { PURGED, MISSING, PROTECTED }
    Result purge(ManagedInputFileRepository.ManagedInputFile managedFile);
}
