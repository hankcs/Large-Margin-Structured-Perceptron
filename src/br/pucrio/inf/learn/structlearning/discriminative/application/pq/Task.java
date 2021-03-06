package br.pucrio.inf.learn.structlearning.discriminative.application.pq;

public class Task {
	private int start;
	private int end;
	private double prize;
	private int predecessorIndex;
	//Atributes below refer to the Quotation Extraction problem.
	//Do not need to be used
	private int quotationPosition;
	private int coreferencePosition;
	private int[] quotationIndex;
	private int[] coreferenceIndex;
	
	public Task(int start, int end, double prize) {
		this.start = start;
		this.end   = end;
		this.prize = prize;
		
		this.predecessorIndex = -1;
		
		this.quotationPosition   = -1;
		this.coreferencePosition = -1;
		
		this.quotationIndex    = null;
		this.coreferenceIndex    = null;
	}
	
	public Task(int start, int end, double prize, int quotationPosition, int coreferencePosition, int[] quotationIndex, int[] coreferenceIndex) {
		this.start = start;
		this.end   = end;
		this.prize = prize;
		
		this.quotationPosition   = quotationPosition;
		this.coreferencePosition = coreferencePosition;
		
		this.quotationIndex    = new int[2];
		this.quotationIndex[0] = quotationIndex[0];
		this.quotationIndex[1] = quotationIndex[1];
		
		this.coreferenceIndex    = new int[2];
		this.coreferenceIndex[0] = coreferenceIndex[0];
		this.coreferenceIndex[1] = coreferenceIndex[1];
		
		this.predecessorIndex = -1;
	}
	
	public Task(Task t) {
		this(t.getStart(), t.getEnd(), t.getPrize(), t.getQuotationPosition(), t.getCoreferencePosition(), t.getQuotationIndex(), t.getCoreferenceIndex());
		this.predecessorIndex = t.getPredecessorIndex();
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}

	public double getPrize() {
		return prize;
	}

	public void setPrize(double prize) {
		this.prize = prize;
	}
	
	public int getQuotationPosition() {
		return quotationPosition;
	}

	public void setQuotationPosition(int quotationPosition) {
		this.quotationPosition = quotationPosition;
	}
	
	public int getCoreferencePosition() {
		return coreferencePosition;
	}

	public void setCoreferencePosition(int coreferencePosition) {
		this.coreferencePosition = coreferencePosition;
	}

	public int getPredecessorIndex() {
		return predecessorIndex;
	}

	public void setPredecessorIndex(int predecessorIndex) {
		this.predecessorIndex = predecessorIndex;
	}

	public int[] getQuotationIndex() {
		return quotationIndex;
	}

	public int[] getCoreferenceIndex() {
		return coreferenceIndex;
	}
}
