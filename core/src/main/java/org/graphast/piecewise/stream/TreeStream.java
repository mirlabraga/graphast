package org.graphast.piecewise.stream;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.graphast.piecewise.Function;
import org.graphast.piecewise.GeneratorFunctionLoess;
import org.graphast.piecewise.IGeneratorFunction;
import org.graphast.piecewise.IManipulatorEngine;

public class TreeStream implements IManipulatorEngine {
	
	static boolean INIT = false;
    private No root;
    private TreeStream leftTree;
    private TreeStream rightTree;

    public TreeStream() {
	}
    
    public TreeStream(long timestamp) {
    	Function functionInitial = new Function(timestamp);
		this.setRoot(new No(functionInitial));
   	}
    
	public TreeStream getRightTree() {
        return rightTree;
    }

    public void setArvoreDireita(TreeStream rightTree) {
        this.rightTree = rightTree;
    }

    public TreeStream getLeftTree() {
        return leftTree;
    }

    public void setArvoreEsquerda(TreeStream leftTree) {
        this.leftTree = leftTree;
    }

    public No getRoot() {
        return root;
    }

    public void setRoot(No root) {
        this.root = root;
    }

    public void insertFunction(long idEdge,long timestamp) {
    	
    	IGeneratorFunction generatorFunction = new GeneratorFunctionLoess();
        No no = new No(generatorFunction.gerFuntionEdge(idEdge, timestamp));
        insert(no);
    }

    public void insert(No no) {
        if (this.root == null) {
            this.root = no;
        } else {
            if (no.getFunction().getX() > this.root.getFunction().getX()) {
                if (this.rightTree == null) {
                    this.rightTree = new TreeStream();
                }
                this.rightTree.insert(no);
            } else if (no.getFunction().getX() < this.root.getFunction().getX()) {
                if (this.leftTree == null) {
                    this.leftTree = new TreeStream();
                }
                this.leftTree.insert(no);
            }
        }
    }

    public void percorrerInOrder() {
        if (this.root == null) {
           return;
        }

        if (this.leftTree != null) {
            this.leftTree.percorrerInOrder();
        }

        System.out.println("X: " + this.root.getFunction().getX());
        
        if (this.rightTree != null) {
            this.rightTree.percorrerInOrder();
        }
    }

    public void percorrerPreOrder() {
        if (this.root == null) {
           return;
        }

        System.out.println("X: " + this.root.getFunction().getX());

        if (this.leftTree != null) {
            this.leftTree.percorrerPreOrder();
        }

        if (this.rightTree != null) {
            this.rightTree.percorrerPreOrder();
        }
    }

    public void percorrerPostOrder() {
        if (this.root == null) {
           return;
        }

        if (this.leftTree != null) {
            this.leftTree.percorrerPostOrder();
        }

        if (this.rightTree != null) {
            this.rightTree.percorrerPostOrder();
        }

        System.out.println("X: " + this.root.getFunction().getX());
    }

    public Function find(long timestamp) {
        if (this.root == null) {
            return null;
        } else {
            if (timestamp == this.root.getFunction().getX()) {
                return this.root.getFunction();
            } else {
                if (timestamp > this.root.getFunction().getX()) {
                    if (this.rightTree == null) {
                        return null;
                    }
                    return this.rightTree.find(timestamp);
                } else {
                    if (this.leftTree == null) {
                        return null;
                    }
                    return this.leftTree.find(timestamp);
                }
            }
        }
    }

    public class No {
    	
        private Function function;

        public No(Function function) {
            this.function = function;
        }

        public Function getFunction() {
            return function;
        }

        public void setFunction(Function function) {
            this.function = function;
        }
    }
    
    public  void init() {
    	
    	Calendar root = new GregorianCalendar();   
	    Calendar date = new GregorianCalendar();   
	    root.setTime(new Date());
	    date.set(Calendar.HOUR_OF_DAY, 12);
	    date.set(Calendar.MINUTE, 0);
	    date.set(Calendar.SECOND, 0);
	    
	    new TreeStream(date.getTimeInMillis());
    	INIT = true;
    }

	@Override
	public Function run(long x) {
		
		init();
		insertFunction(0l, x);
		
		return null;
	}
}