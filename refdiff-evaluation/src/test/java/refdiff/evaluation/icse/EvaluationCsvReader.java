package refdiff.evaluation.icse;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import refdiff.evaluation.RefactoringRelationship;
import refdiff.evaluation.RefactoringSet;
import refdiff.evaluation.RefactoringType;
import refdiff.evaluation.ResultComparator;

public class EvaluationCsvReader {
	public static void main(String[] args) throws Exception {
		eval();
	}
	
	public static ResultComparator buildResultComparator(IcseDataset data, List<ResultCommit> list) throws FileNotFoundException, IOException {
		ResultComparator rc2 = new ResultComparator();
		Map<String, RefactoringSet> mapRs = new HashMap<>();
		Map<String, RefactoringSet> mapRsNotExpected = new HashMap<>();
		for (RefactoringSet rs : data.getExpected()) {
			RefactoringSet rs2 = new RefactoringSet(rs.getProject(), rs.getRevision());
			mapRs.put(rs.getRevision(), rs2);
			rs2.add(rs.getRefactorings());
			rc2.expect(rs2);
		}
		for (RefactoringSet rs : data.getNotExpected()) {
			RefactoringSet rs2 = new RefactoringSet(rs.getProject(), rs.getRevision());
			mapRsNotExpected.put(rs.getRevision(), rs2);
			rs2.add(rs.getRefactorings());
			rc2.dontExpect(rs2);
		}
		if (list != null) {
			for (ResultCommit commitResult : list) {
				String url = commitResult.commitUrl;
				String commit = url.substring(url.lastIndexOf("/") + 1);
				
				RefactoringSet expectedRefactorings = mapRs.get(commit);
				RefactoringSet notExpectedRefactorings = mapRsNotExpected.get(commit);
				for (ResultRow row : commitResult.rows) {
					boolean evaluatedAsTp = (row.resultA.equals("TP") && row.resultB.equals("TP")) || row.resultFinal.equals("TP");
					if (evaluatedAsTp) {
						RefactoringType refType = RefactoringType.fromName(row.refType);
						expectedRefactorings.add(new RefactoringRelationship(refType, row.n1, row.n2));
					}
					boolean evaluatedAsFp = (row.resultA.equals("FP") && row.resultB.equals("FP")) || row.resultFinal.equals("FP");
					if (evaluatedAsFp) {
						RefactoringType refType = RefactoringType.fromName(row.refType);
						notExpectedRefactorings.add(new RefactoringRelationship(refType, row.n1, row.n2));
					}
				}
			}
		}
		return rc2;
	}
	
	public static void eval() throws Exception {
		
		IcseDataset data = new IcseDataset();
		List<ResultCommit> list = readRefDiffResults();
		
		ResultComparator rc = buildResultComparator(data, null);
		ResultComparator rc2 = buildResultComparator(data, list);
		
		for (RefactoringSet rs : data.getExpected()) {
			String project = rs.getProject();
			String commit = rs.getRevision();
			rc.compareWith("RefDiff", new RefactoringSet(project, commit));
			rc2.compareWith("RefDiff", new RefactoringSet(project, commit));
		}
		
		Map<String, ResultRow> map = new HashMap<>();
		for (ResultCommit commitResult : list) {
			String url = commitResult.commitUrl;
			String project = url.substring(0, url.lastIndexOf("/commit/")) + ".git";
			String commit = url.substring(url.lastIndexOf("/") + 1);
			
			RefactoringSet rs = new RefactoringSet(project, commit);
			
			// System.out.println(commitResult.commitUrl);
			for (ResultRow row : commitResult.rows) {
				// System.out.println(row);
				if (!row.description.isEmpty()) {
					RefactoringType refType = RefactoringType.fromName(row.refType);
					rs.add(new RefactoringRelationship(refType, row.n1, row.n2));
					map.put(getKey(commit, refType, row.n1, row.n2), row);
				}
			}
			
			rc.compareWith("RefDiff", rs);
			rc2.compareWith("RefDiff", rs);
		}
		
		rc2.compareWith("RMiner", data.getrMinerRefactorings());
		
		rc.printDetails(System.out, RunIcseEval.refactoringTypes, "RefDiff", (RefactoringSet expected, RefactoringRelationship r, String label, String cause) -> {
			ResultRow row = map.get(getKey(expected.getRevision(), r.getRefactoringType(), r.getEntityBefore(), r.getEntityAfter()));
			if (row != null) {
				System.out.printf("\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", row.description, label, row.resultA, row.commentA, row.resultB, row.commentB, row.getResult2(label), row.resultC, row.commentC, row.resultFinal, cause != null ? cause : row.commentFinal, row.getResult3(label));
			} else {
				System.out.printf("\t\t%s\t\t\t\t\t%s\t\t\t\t\t%s", label, label, label);
			}
		});
		System.out.println();
		rc.printSummary(System.out, RunIcseEval.refactoringTypes);
		
		rc2.printSummary(System.out, RunIcseEval.refactoringTypes);
	}
	
	private static String getKey(String commit, RefactoringType refType, String n1, String n2) {
		return commit + " " + refType.name() + " " + n1 + " " + n2;
	}
	
	public static List<ResultCommit> readRefDiffResults() throws IOException, FileNotFoundException {
		List<ResultCommit> list = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader("data/java-evaluation/FixExtractBefore.txt"))) {
			String line;
			String commitUrl = "";
			ResultCommit resultCommit = null;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\t", -1);
				String c0 = parts[0];
				if (c0.startsWith("https://")) {
					commitUrl = c0;
					resultCommit = new ResultCommit();
					list.add(resultCommit);
					resultCommit.commitUrl = commitUrl;
				} else {
					ResultRow row = new ResultRow();
					row.commitUrl = commitUrl;
					row.refType = parts[0];
					row.n1 = parts[1];
					row.n2 = parts[2];
					row.description = parts[3];
					row.result1 = parts[4];
					row.resultA = parts[5];
					row.commentA = parts[6];
					row.resultB = parts[7];
					row.commentB = parts[8];
					row.resultC = parts[10];
					row.commentC = parts[11];
					row.resultFinal = parts[12];
					row.commentFinal = parts[13];
					resultCommit.rows.add(row);
				}
			}
		}
		return list;
	}
	
	public static class ResultCommit {
		public String commitUrl;
		public List<ResultRow> rows = new ArrayList<>();
	}
	
	public static class ResultRow {
		public String commitUrl;
		public String refType;
		public String n1;
		public String n2;
		public String description;
		public String result1;
		public String resultA;
		public String commentA;
		public String resultB;
		public String commentB;
		public String resultC;
		public String commentC;
		public String commentFinal;
		public String resultFinal;
		
		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return String.format("%s\t%s\t%s\t%s\t%s\t%s", n1, n2, resultA, commentA, resultB, commentB);
		}
		
		public String getResult2(String result1) {
			if (result1.equals("FP?")) {
				if (resultA.equals(resultB)) {
					return resultA;
				} else {
					return "FP?";
				}
			} else {
				return result1;
			}
		}
		
		public String getResult3(String result1) {
			if (resultFinal.isEmpty()) {
				return getResult2(result1);
			} else {
				return resultFinal;
			}
		}
	}
	
}