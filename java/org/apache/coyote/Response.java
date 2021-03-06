/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.MediaType;
import org.apache.tomcat.util.res.StringManager;

import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Response object.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Harish Prabandham
 * @author Hans Bergsten [hans@gefionsoftware.com]
 * @author Remy Maucherat
 */
public final class Response {

    private static final StringManager sm =
            StringManager.getManager(Constants.Package);

    // ----------------------------------------------------- Class Variables

    /**
     * Default locale as mandated by the spec.
     */
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();


    // ----------------------------------------------------- Instance Variables
    /**
     * Response headers.
     */
    protected final MimeHeaders headers = new MimeHeaders();
    /**
     * Notes.
     */
    protected final Object[] notes = new Object[Constants.MAX_NOTES];
    private final Object nonBlockingStateLock = new Object();
    /**
     * Action hook.
     */
    public ActionHook hook;
    /**
     * Status code.
     */
    protected int status = 200;
    /**
     * Status message.
     */
    protected String message = null;
    /**
     * Associated output buffer.
     */
    protected OutputBuffer outputBuffer;
    /**
     * Committed flag.
     */
    protected boolean commited = false;
    /**
     * HTTP specific fields.
     */
    protected String contentType = null;
    protected String contentLanguage = null;
    protected String characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
    protected long contentLength = -1;
    /**
     * Holds request error exception.
     */
    protected Exception errorException = null;
    /**
     * Has the charset been explicitly set.
     */
    protected boolean charsetSet = false;
    protected Request req;
    /*
     * State for non-blocking output is maintained here as it is the one point
     * easily reachable from the CoyoteOutputStream and the Processor which both
     * need access to state.
     */
    protected volatile WriteListener listener;
    private Locale locale = DEFAULT_LOCALE;

    // ------------------------------------------------------------- Properties
    // General informations
    private long contentWritten = 0;
    private long commitTime = -1;
    private boolean fireListener = false;
    private boolean registeredForWrite = false;

    public Request getRequest() {
        return req;
    }

    public void setRequest( Request req ) {
        this.req=req;
    }


    // -------------------- Per-Response "notes" --------------------

    public void setOutputBuffer(OutputBuffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }

    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    // -------------------- Actions --------------------

    public ActionHook getHook() {
        return hook;
    }


    // -------------------- State --------------------

    public void setHook(ActionHook hook) {
        this.hook = hook;
    }

    public final void setNote(int pos, Object value) {
        notes[pos] = value;
    }

    public final Object getNote(int pos) {
        return notes[pos];
    }

    public void action(ActionCode actionCode, Object param) {
        if (hook != null) {
            if( param==null )
                hook.action(actionCode, this);
            else
                hook.action(actionCode, param);
        }
    }

    public int getStatus() {
        return status;
    }

    /**
     * Set the response status
     */
    public void setStatus( int status ) {
        this.status = status;
    }

    /**
     * Get the status message.
     */
    public String getMessage() {
        return message;
    }

    // -----------------Error State --------------------

    /**
     * Set the status message.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isCommitted() {
        return commited;
    }

    public void setCommitted(boolean v) {
        if (v && !this.commited) {
            this.commitTime = System.currentTimeMillis();
        }
        this.commited = v;
    }


    // -------------------- Methods --------------------

    /**
     * Return the time the response was committed (based on System.currentTimeMillis).
     *
     * @return the time the response was committed
     */
    public long getCommitTime() {
        return commitTime;
    }


    // -------------------- Headers --------------------

    /**
     * Get the Exception that occurred during request
     * processing.
     */
    public Exception getErrorException() {
        return errorException;
    }

    /**
     * Set the error Exception that occurred during
     * request processing.
     */
    public void setErrorException(Exception ex) {
        errorException = ex;
    }

    public boolean isExceptionPresent() {
        return ( errorException != null );
    }

    public void reset() throws IllegalStateException {

        if (commited) {
            throw new IllegalStateException();
        }

        recycle();

        // Reset the stream
        action(ActionCode.RESET, this);
    }

    /**
     * Warning: This method always returns <code>false</code> for Content-Type
     * and Content-Length.
     */
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }

    public void setHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        headers.setValue(name).setString( value);
    }


    // -------------------- I18N --------------------

    public void addHeader(String name, String value) {
        addHeader(name, value, null);
    }

    public void addHeader(String name, String value, Charset charset) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        MessageBytes mb = headers.addValue(name);
        if (charset != null) {
            mb.setCharset(charset);
        }
        mb.setString(value);
    }

    /**
     * Set internal fields for special header names.
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader( String name, String value) {
        // XXX Eliminate redundant fields !!!
        // ( both header and in special fields )
        if( name.equalsIgnoreCase( "Content-Type" ) ) {
            setContentType( value );
            return true;
        }
        if( name.equalsIgnoreCase( "Content-Length" ) ) {
            try {
                long cL=Long.parseLong( value );
                setContentLength( cL );
                return true;
            } catch( NumberFormatException ex ) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        }
        return false;
    }

    /** Signal that we're done with the headers, and body will follow.
     *  Any implementation needs to notify ContextManager, to allow
     *  interceptors to fix headers.
     */
    public void sendHeaders() {
        action(ActionCode.COMMIT, this);
        setCommitted(true);
    }

    public Locale getLocale() {
        return locale;
    }

    /**
     * Called explicitly by user to set the Content-Language and
     * the default encoding
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.getLanguage();
        if ((contentLanguage != null) && (contentLanguage.length() > 0)) {
            String country = locale.getCountry();
            StringBuilder value = new StringBuilder(contentLanguage);
            if ((country != null) && (country.length() > 0)) {
                value.append('-');
                value.append(country);
            }
            contentLanguage = value.toString();
        }

    }

    /**
     * Return the content language.
     */
    public String getContentLanguage() {
        return contentLanguage;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    /*
     * Overrides the name of the character encoding used in the body
     * of the response. This method must be called prior to writing output
     * using getWriter().
     *
     * @param charset String containing the name of the character encoding.
     */
    public void setCharacterEncoding(String charset) {

        if (isCommitted())
            return;
        if (charset == null)
            return;

        characterEncoding = charset;
        charsetSet=true;
    }

    public void setContentTypeNoCharset(String type) {
        this.contentType = type;
    }

    public String getContentType() {

        String ret = contentType;

        if (ret != null
            && characterEncoding != null
            && charsetSet) {
            ret = ret + ";charset=" + characterEncoding;
        }

        return ret;
    }

    /**
     * Sets the content type.
     *
     * This method must preserve any response charset that may already have
     * been set via a call to response.setContentType(), response.setLocale(),
     * or response.setCharacterEncoding().
     *
     * @param type the content type
     */
    public void setContentType(String type) {

        if (type == null) {
            this.contentType = null;
            return;
        }

        MediaType m = null;
        try {
             m = MediaType.parseMediaType(new StringReader(type));
        } catch (IOException e) {
            // Ignore - null test below handles this
        }
        if (m == null) {
            // Invalid - Assume no charset and just pass through whatever
            // the user provided.
            this.contentType = type;
            return;
        }

        this.contentType = m.toStringNoCharset();

        String charsetValue = m.getCharset();

        if (charsetValue != null) {
            charsetValue = charsetValue.trim();
            if (charsetValue.length() > 0) {
                charsetSet = true;
                this.characterEncoding = charsetValue;
            }
        }
    }

    // --------------------

    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public long getContentLengthLong() {
        return contentLength;
    }

    /**
     * Write a chunk of bytes.
     */
    public void doWrite(ByteChunk chunk) throws IOException {
        outputBuffer.doWrite(chunk);
        contentWritten+=chunk.getLength();
    }

    public void recycle() {

        contentType = null;
        contentLanguage = null;
        locale = DEFAULT_LOCALE;
        characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
        charsetSet = false;
        contentLength = -1;
        status = 200;
        message = null;
        commited = false;
        commitTime = -1;
        errorException = null;
        headers.clear();
        // Servlet 3.1 non-blocking write listener
        listener = null;
        fireListener = false;
        registeredForWrite = false;

        // update counters
        contentWritten=0;
    }

    /**
     * Bytes written by application - i.e. before compression, chunking, etc.
     */
    public long getContentWritten() {
        return contentWritten;
    }

    /**
     * Bytes written to socket - i.e. after compression, chunking, etc.
     */
    public long getBytesWritten(boolean flush) {
        if (flush) {
            action(ActionCode.CLIENT_FLUSH, this);
        }
        return outputBuffer.getBytesWritten();
    }

    public WriteListener getWriteListener() {
        return listener;
}

    public void setWriteListener(WriteListener listener) {
        if (listener == null) {
            throw new NullPointerException(
                    sm.getString("response.nullWriteListener"));
        }
        if (getWriteListener() != null) {
            throw new IllegalStateException(
                    sm.getString("response.writeListenerSet"));
        }
        // Note: This class is not used for HTTP upgrade so only need to test
        //       for async
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException(
                    sm.getString("response.notAsync"));
        }

        this.listener = listener;

        // The container is responsible for the first call to
        // listener.onWritePossible(). If isReady() returns true, the container
        // needs to call listener.onWritePossible() from a new thread. If
        // isReady() returns false, the socket will be registered for write and
        // the container will call listener.onWritePossible() once data can be
        // written.
        if (isReady()) {
            synchronized (nonBlockingStateLock) {
                // Ensure we don't get multiple write registrations if
                // ServletOutputStream.isReady() returns false during a call to
                // onDataAvailable()
                registeredForWrite = true;
                // Need to set the fireListener flag otherwise when the
                // container tries to trigger onWritePossible, nothing will
                // happen
                fireListener = true;
            }
            action(ActionCode.DISPATCH_WRITE, null);
            if (!ContainerThreadMarker.isContainerThread()) {
                // Not on a container thread so need to execute the dispatch
                action(ActionCode.DISPATCH_EXECUTE, null);
            }
        }
    }

    public boolean isReady() {
        if (listener == null) {
            // TODO i18n
            throw new IllegalStateException("not in non blocking mode.");
        }
        // Assume write is not possible
        boolean ready = false;
        synchronized (nonBlockingStateLock) {
            if (registeredForWrite) {
                fireListener = true;
                return false;
            }
            ready = checkRegisterForWrite();
            fireListener = !ready;
        }
        return ready;
    }

    public boolean checkRegisterForWrite() {
        AtomicBoolean ready = new AtomicBoolean(false);
        synchronized (nonBlockingStateLock) {
            if (!registeredForWrite) {
                action(ActionCode.NB_WRITE_INTEREST, ready);
                registeredForWrite = !ready.get();
            }
        }
        return ready.get();
    }

    public void onWritePossible() throws IOException {
        // Any buffered data left over from a previous non-blocking write is
        // written in the Processor so if this point is reached the app is able
        // to write data.
        boolean fire = false;
        synchronized (nonBlockingStateLock) {
            registeredForWrite = false;
            if (fireListener) {
                fireListener = false;
                fire = true;
            }
        }
        if (fire) {
            listener.onWritePossible();
        }
    }
}
