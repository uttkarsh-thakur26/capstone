package in.edu.bennett.capstone.knowledge.document;

import java.io.InputStream;

/** An opened stored file and what the download response says about it (DOC-6). */
record DocumentFile(InputStream content, String filename, String contentType, long sizeBytes) {}
