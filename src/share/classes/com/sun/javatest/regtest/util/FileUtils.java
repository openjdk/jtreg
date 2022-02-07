package com.sun.javatest.regtest.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utilities for handling files.
 */
public class FileUtils {
    /**
     * An unchecked IO exception to be thrown when various NIO file operations fail,
     * and throw a checked IO exception.
     */
    public static class NIOFileOperationException extends UncheckedIOException {
        private static final long serialVersionUID = 0;

        /**
         * The operation that caused an exception.
         */
        enum Op {MKDIRS, LAST_MOD, LIST, SIZE}

        /**
         * Creates an unchecked IO exception for a checked IO exception
         * that was caused by an operation on a file.
         *
         * @param op    the operation
         * @param p     the file
         * @param cause the IO exception
         */
        NIOFileOperationException(Op op, Path p, IOException cause) {
            super(getMessage(op, p), cause);
        }

        private static String getMessage(Op op, Path p) {
            switch (op) {
                case MKDIRS:
                    return "Cannot create directories for " + p;
                case LAST_MOD:
                    return "Cannot access last-modified time for " + p;
                case LIST:
                    return "Cannot list directory " + p;
                case SIZE:
                    return "Cannot determine size of file " + p;
                default:
                    return "Cannot perform unknown operation for " + p;
            }
        }
    }

    private FileUtils() { }

    /**
     * Returns the size of a file, in  bytes.
     *
     * @param p the file
     * @return the size of a file, in  bytes
     * @throws NIOFileOperationException if an IO exception arose while accessing the size
     */
    public static long size(Path p) throws NIOFileOperationException {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new NIOFileOperationException(NIOFileOperationException.Op.SIZE, p, e);
        }
    }

    /**
     * Returns the time that a file was last modified.
     *
     * @param p the file
     * @return the time the file was last modified
     * @throws NIOFileOperationException if an IO exception arose while accessing the time
     */
    public static FileTime getLastModifiedTime(Path p) throws NIOFileOperationException {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            throw new NIOFileOperationException(NIOFileOperationException.Op.LAST_MOD, p, e);
        }
    }

    /**
     * Compares the times that a pair of files were last modified.
     *
     * @param p1 the first file
     * @param p2 the second file
     * @return {@code -1}, {@code 0} or {@code 1} according to whether the last modified time of the first file
     *          is less than, equal to, or greater than the last modified time of the second file
     *
     * @throws NIOFileOperationException if an IO exception arose while accessing the time for either file
     */
    public static int compareLastModifiedTimes(Path p1, Path p2) {
        return getLastModifiedTime(p1).compareTo(getLastModifiedTime(p2));
    }

    /**
     * Creates a directory by creating all non-existent parent directories first.
     * No exception is thrown if the directory already exists.
     *
     * @param dir the directory
     *
     * @throws NIOFileOperationException if an IO exception arose while creating the directories
     *
     * @see Files#createDirectories(Path, FileAttribute[])
     */
    public static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new NIOFileOperationException(NIOFileOperationException.Op.MKDIRS, dir, e);
        }
    }

    /**
     * Returns the list of contents of a directory.
     *
     * @param dir the directory
     *
     * @return the list of contents
     */
    public static List<Path> listFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                files.add(p);
            }
        } catch (IOException e) {
            throw new NIOFileOperationException(NIOFileOperationException.Op.LIST, dir, e);
        }
        return files;
    }

    /**
     * Converts an array of paths to an array of file.
     *
     * @param paths the array of paths
     * @return the array of files
     */
    public static File[] toFiles(Path[] paths) {
        return Stream.of(paths)
                .map(Path::toFile)
                .collect(Collectors.toList())
                .toArray(new File[0]);
    }
}
