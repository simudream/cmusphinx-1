/*
 * 
 * Copyright 1999-2004 Carnegie Mellon University.  
 * Portions Copyright 2004 Sun Microsystems, Inc.  
 * Portions Copyright 2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.cmu.sphinx.recognizer;

import edu.cmu.sphinx.decoder.Decoder;
import edu.cmu.sphinx.instrumentation.Monitor;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.result.ResultListener;
import edu.cmu.sphinx.util.props.*;

import java.io.IOException;
import java.util.*;

/**
 * The Sphinx-4 recognizer. This is the main entry point for Sphinx-4. Typical usage of a recognizer is like so:
 * <p/>
 * <pre><code>
 *  public void recognizeDigits() {
 *      URL digitsConfig = new URL("file:./digits.xml");
 *      ConfigurationManager cm = new ConfigurationManager(digitsConfig);
 *      Recognizer sphinxDigitsRecognizer
 *          = (Recognizer) cm.lookup("digitsRecognizer&quot");
 *      boolean done = false;
 *      Result result;
 * <p/>
 *      sphinxDigitsRecognizer.allocate();
 * <p/>
 *     // echo spoken digits, quit when 'nine' is spoken
 * <p/>
 *      while (!done) {
 *           result = sphinxDigitsRecognizer.recognize();
 *           System.out.println(&quot;Result: &quot; + result);
 *           done = result.toString().equals(&quot;nine&quot;);
 *      }
 * <p/>
 *      sphinxDigitsRecognizer.deallocate();
 *   }
 * </code></pre>
 * <p/>
 * Note that some Recognizer methods may throw an IllegalStateException if the recognizer is not in the proper state
 */
public class Recognizer implements Configurable {

    /** Property name for the decoder to be used by this recognizer. */
    @S4Component(type = Decoder.class)
    public final static String PROP_DECODER = "decoder";

    /** Property name for the set of monitors for this recognizer */
    @S4ComponentList(type = StateListener.class)
    public final static String PROP_MONITORS = "monitors";


    private String name;
    private Decoder decoder;
    private RecognizerState currentState = RecognizerState.DEALLOCATED;

    private List<StateListener> stateListeners = Collections.synchronizedList(new ArrayList<StateListener>());
    private List monitors;


    /*
    * (non-Javadoc)
    *
    * @see edu.cmu.sphinx.util.props.Configurable#getConfigurationInfo()
    */
    public static Map getConfigurationInfo() {
        Map info = new HashMap();
        info.put(new String("PROP_MONITORS_TYPE"), new String("COMPONENT_LIST"));
        info.put(new String("PROP_DECODER_CLASSTYPE"), new String("edu.cmu.sphinx.instrumentation.Monitor"));
        info.put(new String("PROP_DECODER_TYPE"), new String("COMPONENT"));
        info.put(new String("PROP_DECODER_CLASSTYPE"), new String("edu.cmu.sphinx.decoder.Decoder"));
        return info;
    }


    /* (non-Javadoc)
    * @see edu.cmu.sphinx.util.props.Configurable#register(java.lang.String, edu.cmu.sphinx.util.props.Registry)
    */
    public void register(String name, Registry registry) throws PropertyException {
        this.name = name;
        registry.register(PROP_DECODER, PropertyType.COMPONENT);
        registry.register(PROP_MONITORS, PropertyType.COMPONENT_LIST);
    }


    /* (non-Javadoc)
    * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
    */
    public void newProperties(PropertySheet ps) throws PropertyException {
        decoder = (Decoder) ps.getComponent(PROP_DECODER, Decoder.class);
        monitors = ps.getComponentList(PROP_MONITORS, Monitor.class);
    }


    /**
     * Performs recognition for the given number of input frames, or until a 'final' result is generated. This method
     * should only be called when the recognizer is in the <code>allocated</code> state.
     *
     * @param referenceText what was actually spoken
     * @return a recognition result
     * @throws IllegalStateException if the recognizer is not in the <code>ALLOCATED</code> state
     */
    public Result recognize(String referenceText) throws IllegalStateException {
        Result result = null;
        checkState(RecognizerState.READY);
        try {
            setState(RecognizerState.RECOGNIZING);
            result = decoder.decode(referenceText);
        } finally {
            setState(RecognizerState.READY);
        }
        return result;
    }


    /**
     * Performs recognition for the given number of input frames, or until a 'final' result is generated. This method
     * should only be called when the recognizer is in the <code>allocated</code> state.
     *
     * @return a recognition result
     * @throws IllegalStateException if the recognizer is not in the <code>ALLOCATED</code> state
     */
    public Result recognize() throws IllegalStateException {
        return recognize(null);
    }


    /**
     * Checks to ensure that the recognizer is in the given state.
     *
     * @param desiredState the state that the recognizer should be in
     * @throws IllegalStateException if the recognizer is not in the desired state.
     */
    private void checkState(RecognizerState desiredState) {
        if (currentState != desiredState) {
            throw new IllegalStateException("Expected state " + desiredState
                    + " actual state " + currentState);
        }
    }


    /**
     * sets the current state
     *
     * @param newState the new state
     */
    private void setState(RecognizerState newState) {
        currentState = newState;
        synchronized (stateListeners) {
            for (Iterator<StateListener> i = stateListeners.iterator(); i.hasNext();) {
                StateListener stateListener = i.next();
                stateListener.statusChanged(currentState);
            }
        }
    }


    /**
     * Allocate the resources needed for the recognizer. Note this method make take some time to complete. This method
     * should only be called when the recognizer is in the <code> deallocated </code> state.
     *
     * @throws IllegalStateException if the recognizer is not in the <code>DEALLOCATED</code> state
     */
    public void allocate() throws IllegalStateException, IOException {
        checkState(RecognizerState.DEALLOCATED);
        setState(RecognizerState.ALLOCATING);
        decoder.allocate();
        setState(RecognizerState.ALLOCATED);
        setState(RecognizerState.READY);
    }


    /**
     * Deallocates the recognizer. This method should only be called if the recognizer is in the <code> allocated
     * </code> state.
     *
     * @throws IllegalStateException if the recognizer is not in the <code>ALLOCATED</code> state
     */
    public void deallocate() throws IllegalStateException {
        checkState(RecognizerState.READY);
        setState(RecognizerState.DEALLOCATING);
        decoder.deallocate();
        setState(RecognizerState.DEALLOCATED);
    }


    /**
     * Retrieves the recognizer state. This method can be called in any state.
     *
     * @return the recognizer state
     */
    public RecognizerState getState() {
        return currentState;
    }


    /** Resets the monitors monitoring this recognizer */
    public void resetMonitors() {
        for (Iterator i = monitors.iterator(); i.hasNext();) {
            Object o = i.next();
            if (o instanceof Resetable) {
                Resetable r = (Resetable) o;
                r.reset();
            }
        }
    }


    /**
     * Adds a result listener to this recognizer. A result listener is called whenever a new result is generated by the
     * recognizer. This method can be called in any state.
     *
     * @param resultListener the listener to add
     */
    public void addResultListener(ResultListener resultListener) {
        decoder.addResultListener(resultListener);
    }


    /**
     * Adds a status listener to this recognizer. The status listener is called whenever the status of the recognizer
     * changes. This method can be called in any state.
     *
     * @param stateListener the listener to add
     */
    public void addStateListener(StateListener stateListener) {
        stateListeners.add(stateListener);
    }


    /**
     * Removes a previously added result listener. This method can be called in any state.
     *
     * @param resultListener the listener to remove
     */
    public void removeResultListener(ResultListener resultListener) {
        decoder.removeResultListener(resultListener);
    }


    /**
     * Removes a previously added state listener. This method can be called in any state.
     *
     * @param stateListener the state listener to remove
     */
    public void removeStateListener(StateListener stateListener) {
        stateListeners.remove(stateListener);
    }


    /* (non-Javadoc)
    * @see edu.cmu.sphinx.util.props.Configurable#getName()
    */
    public String getName() {
        return name;
    }


    /** @return the decoder containing all the components of this recognizer */
    public Decoder getDecoder() {
        return decoder;
    }


    /* (non-Javadoc)
    * @see java.lang.Object#toString()
    */
    public String toString() {
        return "Recognizer: " + getName() + " State: " + currentState;
    }

}
