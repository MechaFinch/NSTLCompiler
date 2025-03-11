package notsotiny.lang.compiler.irgen;

import java.util.logging.Level;
import java.util.logging.Logger;

import fr.cenotelie.hime.redist.ASTNode;
import fr.cenotelie.hime.redist.SymbolType;
import fr.cenotelie.hime.redist.TextPosition;
import notsotiny.lang.compiler.ASTUtil;

/**
 * Methods for logging with source information
 */
public class ASTLogger {
    
    private Logger log;
    
    /**
     * @param log
     */
    public ASTLogger(Logger log) {
        this.log = log;
    }
    
    /**
     * @param node
     * @return
     */
    private String getSourceInfoString(ASTNode node) {
        TextPosition pos = ASTUtil.getPosition(node);
         
        return "(" + pos.getLine() + ":" + pos.getColumn() + "): ";
    }
    
    /**
     * General log
     * @param level
     * @param node
     * @param message
     */
    public void log(Level level, ASTNode node, String message) {
        if(this.log.isLoggable(level)) {
            this.log.log(level, getSourceInfoString(node) + message);
        }
    }
    
    /**
     * SEVERE log
     * @param node
     * @param message
     */
    public void severe(ASTNode node, String message) {
        this.log.severe(getSourceInfoString(node) + message);
    }
    
    /**
     * WARNING log
     * @param node
     * @param message
     */
    public void warning(ASTNode node, String message) {
        this.log.warning(getSourceInfoString(node) + message);
    }
    
    /**
     * INFO log
     * @param node
     * @param message
     */
    public void info(ASTNode node, String message) {
        this.log.info(getSourceInfoString(node) + message);
    }
    
    /**
     * FINE log
     * @param node
     * @param message
     */
    public void fine(ASTNode node, String message) {
        this.log.fine(getSourceInfoString(node) + message);
    }
    
    /**
     * FINER log
     * @param node
     * @param message
     */
    public void finer(ASTNode node, String message) {
        this.log.finer(getSourceInfoString(node) + message);
    }
    
    /**
     * FINEST log
     * @param node
     * @param message
     */
    public void finest(ASTNode node, String message) {
        this.log.finest(getSourceInfoString(node) + message);
    }
}
