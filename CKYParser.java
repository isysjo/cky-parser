/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nlp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.lang.Math;

/**
 *
 * @author Isys Johnson, Carlos Salas
 */
public class CKYParser {
    public CKYEntry[][] table;
    public HashMap<String,HashSet> lexical = new HashMap<>(); // maps a rhs to lhs
    public HashMap<String,HashSet> unary = new HashMap<>(); //maps unary rhs to lhs
    public HashMap<String,GrammarRule> binary = new HashMap<>(); // maps rhs of binary rule to full rule
    public HashMap<GrammarRule,GrammarRule> rules = new HashMap<>(); // maps grammar rule with weight 0.0 to actual grammar rule
    
    
    /**
     * Creates new CKYParse that takes a grammar, and saves each
     * binary, unary, and lexical rule separates, along with a set of the total rules.
     * 
     * @param filename
     * @throws IOException 
     */
    public CKYParser(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String ru = "";
        while((ru = br.readLine()) != null) {
            
            
           // each key of the rules with be the string form of the rule with weight 0.0
            GrammarRule rule = new GrammarRule(ru);
            GrammarRule copy = new GrammarRule(rule.toString());
            copy.setWeight(0.0);
            
            rules.put(copy,rule);
            
            String lhs = rule.getLhs();
            ArrayList<String> rhs = rule.getRhs();
            
            // separate binary rules, lexical rules, and unary rules
            if(rule.isLexical()) {
                String lex = rhs.get(0);
                HashSet<String> con = new HashSet<>();
                con.add(lhs);
                if(lexical.containsKey(lex)) {
                   HashSet<String> c = lexical.get(lex);
                   c.addAll(con);
                    lexical.put(lex,c);
                } else lexical.put(lex, con);
            }
                    
             
            else if(rule.numRhsElements() == 1) {
                String un = rhs.get(0);
                HashSet<String> con = new HashSet<>();
                con.add(lhs);
                if(unary.containsKey(un)) {
                   HashSet<String> c = unary.get(un);
                   c.addAll(con);
                    unary.put(un,c);
                } else unary.put(un, con);
            }
            else {
                
                String[] splitRule = copy.toString().split("->");
                binary.put(splitRule[1], rule);
            }
       
        }
        

    }
    /**
     * Takes a sentence and creates a CKY table for it, saving the score of the parse
     * in the final CKYEntry
     * 
     * @param sentence 
     */
    public void parse(String sentence) {
        
        String[] words = sentence.split(" ");
        table = new CKYEntry[words.length][words.length];
        
        int i = 0;
        int j = 0;
        while(i < table.length && j < table.length) {
        // add lexical components to table
        table[i][j] = new CKYEntry(null,null,words[j],true,this);
        i++;
        j++;
                
        }
        //traverse table by diagonals
        i = 1; j = 1;
        int k = 1;
       while( i < table.length-1) {  
          if(j > table.length -1 ) { j = i+1;i++; k++;}
          table[j-k][j] = new CKYEntry(table[j-k][j-1],table[j-k+1][j],words[j],false,this);
          j++;
        }
       
       
       System.out.println(table[0][table.length-1].constit.get("S"));
       
               
    }
    
    private static class CKYEntry {
        CKYParser p;   
        //idea: map contituent with current value in tree                                            m          
        HashMap<String,Double> constit = new HashMap<String,Double>(); // map constituents of entry with the total score at constituent
        HashMap<String,Double> leftcons = new HashMap<>(); // all left constituents
        HashMap<String,Double> downcons = new HashMap<>(); // all down constituents
        String self; // for lexical use
        CKYEntry entry1,entry2; // closest left and bottom entries
        boolean isLexical; // lexical marker
        
        /**
         * Creates CKYEntry and finds constituents
         * 
         * @param left table[i-1][j]
         * @param down table[i][j+1]
         * @param s The actual lexical symbol
         * @param lex Lexical entry flag
         * @param par CKYParser with saves rules
         */
        public CKYEntry(CKYEntry left, CKYEntry down,String s, boolean lex, CKYParser par) {
            this.p = par;
            self = s;
            if(lex) {
                getLexicalConst();
                entry1 = left;
                entry2 = down;
            } else {
                 entry1 = getLeftEntry(left);
                 entry2 = getDownEntry(down);
            
                leftcons.putAll(entry1.leftcons);
                leftcons.putAll(entry1.constit);
                downcons.putAll(entry2.downcons);
                downcons.putAll(entry2.constit);
                self = left.self + " " + down.self;
                getBinaryConst();
            
            }
            
            
            isLexical = lex; 
            checkUnary();
           
          
        }
        
       
        
        /**
         * Get lexical constituents
         */
        public void getLexicalConst() {
            // from parser, assign lexical constituents
            if(p.lexical.containsKey(self))
                for(String l : (HashSet<String>)p.lexical.get(self)) {
                    GrammarRule attempt = new GrammarRule(l,new ArrayList<String>(Arrays.asList(new String[]{self})),true);
                    if(p.rules.containsKey(attempt))
                        constit.put(l, p.rules.get(attempt).getWeight());
                }
        }
        /**
         * Get closest CKYEntry to the left
         * @param e1 CKY Entry
         * @return 
         */
        public CKYEntry getLeftEntry(CKYEntry e1)  {
            // get closest nontempty left entry
            if(e1.constit.isEmpty())
               return getLeftEntry(e1.entry1);
            else return e1;
            
                
        }
        
        /**
         * Get closest CKY Entry below
         * @param e2 CKYEntry
         * @return 
         */
        public CKYEntry getDownEntry(CKYEntry e2) {
            // get closes nonempty bottom entry
            if(e2.constit.isEmpty())
               return getLeftEntry(e2.entry2);
            else return e2;
            
                
        }
        
        /**
         * Checks all all constituents for unary rules, only adds them if
         * unseen or they contribute to the score
         */
        public void checkUnary() {
            // check the unary rules
            HashMap<String,Double> morecons = new HashMap<>();
            for(String r : constit.keySet()) {
                if(p.unary.containsKey(r)) {
                  
                    for(String un : (HashSet<String>)p.unary.get(r)){
                        
                        GrammarRule attempt = new GrammarRule(un,new ArrayList<String>(Arrays.asList(new String[]{r})),false);

                        double currWeight = p.rules.get(attempt).getWeight();
                        if(constit.containsKey(un)) {
                            // only add if increases score
                            double higher = currWeight + constit.get(r);
                            if(higher > currWeight)
                                morecons.put(un, higher);
                            
                        }
                        else  
                            morecons.put(un,currWeight + constit.get(r));
                    }
                     
                }
                    
            }
            
            constit.putAll(morecons);
        }
        /**
         * Searches left and bottom constituents to find matching RHS
         * when matching rule found, add it to total weight of constituent at current entry
         */
        public void getBinaryConst() {
          
            for(String r1 : leftcons.keySet())
                for(String r2 : downcons.keySet()){
                    GrammarRule attempt = new GrammarRule("S",new ArrayList<String>(Arrays.asList(new String[]{r1,r2})),0.0);
                   
                    if (p.binary.containsKey(attempt.toString().split("->")[1])){
                        
                        constit.put(p.binary.get(attempt.toString().split("->")[1]).getLhs(),
                                p.binary.get(attempt.toString().split("->")[1]).getWeight() + leftcons.get(r1) + downcons.get(r2));
                    }
                }
           
            
        }
        
        public String[] rhsForm() {
            return self.split(" ");
        }
        /**
         * 
         * @return Constituents of entry and their accumulated weights 
         */
        public String toString() {
           return constit.toString();
        }
    }
    
    public static void main(String args[]) throws IOException {
       
        //CKYParser parser = new CKYParser(args[0]);
        //BufferedReader r = new BufferedReader(new FileReader(args[1]));
        
        CKYParser parser = new CKYParser("C:\\Users\\JTwal\\Documents\\example.pcfg" );
        BufferedReader r = new BufferedReader(new FileReader("C:\\Users\\JTwal\\Documents\\example.input"));
        String str;
        while((str = r.readLine()) != null) {
            System.out.println(str);
            try{
            parser.parse(str);
            }
            catch(NullPointerException e) {
                System.out.print("NULL");
            } finally{continue; }
            
        
        }
    
    }
}
