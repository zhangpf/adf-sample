package NUDT.utils.extendTools.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.print.attribute.standard.PrinterMessageFromOperator;

import org.junit.Test;

import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.Edge;
import NUDT.utils.extendTools.RoadTools;

public class TestRoadTools {

	@Test
	public void TestCanPass1() {
		System.out.println("Test 1");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(100.0, 0.0), new Point2D(300.0, 0)));
		segs.add(new Line2D(new Point2D(550.0, 0.0), new Point2D(750.0, 0)));
		assertTrue(RoadTools.canPass(edge, segs));
		//if(RoadTools.canPass(edge,  segs))
		//	System.out.println("CanPass.");
		//else 
		//	System.out.println("Blocked!!!");
	}
	
	@Test
	public void TestCanPass2() {
		System.out.println("Test 2");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(0.0, 0.0), new Point2D(200.0, 0)));
		segs.add(new Line2D(new Point2D(400.0, 0.0), new Point2D(600.0, 0)));
		segs.add(new Line2D(new Point2D(800.0, 0.0), new Point2D(1000.0, 0)));
		assertTrue(RoadTools.canPass(edge, segs));
	}
	
	@Test
	public void TestCanPass3() {
		System.out.println("Test 3");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(0.0, 0.0), new Point2D(201.0, 0)));
		segs.add(new Line2D(new Point2D(400.0, 0.0), new Point2D(601.0, 0)));
		segs.add(new Line2D(new Point2D(800.0, 0.0), new Point2D(1000.0, 0)));
		assertFalse(RoadTools.canPass(edge, segs));
	}
	
	@Test
	public void TestCanPass4() {
		System.out.println("Test 4");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(0.0, 0.0), new Point2D(200.0, 0)));
		segs.add(new Line2D(new Point2D(399.0, 0.0), new Point2D(399.0, 0)));
		segs.add(new Line2D(new Point2D(500.0, 0.0), new Point2D(1000.0, 0)));
		assertFalse(RoadTools.canPass(edge, segs));
	}
	
	@Test
	public void TestCanPass5() {
		System.out.println("Test 5");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(0.0, 0.0), new Point2D(1000.0, 0)));
		//segs.add(new Line2D(new Point2D(399.0, 0.0), new Point2D(399.0, 0)));
		//segs.add(new Line2D(new Point2D(500.0, 0.0), new Point2D(1000.0, 0)));
		assertFalse(RoadTools.canPass(edge, segs));
	}
	
	@Test
	public void TestCanPass6() {
		System.out.println("Test 6");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(0.0, 0.0), new Point2D(400.0, 0)));
		segs.add(new Line2D(new Point2D(450.0, 0.0), new Point2D(500.0, 0)));
		segs.add(new Line2D(new Point2D(650.0, 0.0), new Point2D(1000.0, 0)));
		assertFalse(RoadTools.canPass(edge, segs));
	}
	
	@Test
	public void TestCanPass7() {
		System.out.println("Test 7");
		Edge edge = new Edge(new Point2D(0.0, 0.0), new Point2D(1000.0, 0.0));
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(200.0, 0.0), new Point2D(1000.0, 0)));
		//segs.add(new Line2D(new Point2D(300.0, 0.0), new Point2D(500.0, 0)));
		//segs.add(new Line2D(new Point2D(650.0, 0.0), new Point2D(1000.0, 0)));
		assertTrue(RoadTools.canPass(edge, segs));
	}
	
	@Test
	public void TestMergeSegments1()
	{
		System.out.println("Test 8");
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(200.0, 0.0), new Point2D(500.0, 0)));
		segs.add(new Line2D(new Point2D(350.0, 0.0), new Point2D(600.0, 0)));
		segs.add(new Line2D(new Point2D(800.0, 0.0), new Point2D(1000.0, 0)));
		RoadTools.mergeSegments(segs);
		assertTrue(true);
	}
	
	@Test
	public void TestMergeSegments2()
	{
		System.out.println("Test 9");
		List<Line2D> segs = new ArrayList<Line2D>();
		segs.add(new Line2D(new Point2D(200.0, 0.0), new Point2D(500.0, 0)));
		segs.add(new Line2D(new Point2D(350.0, 0.0), new Point2D(600.0, 0)));
		segs.add(new Line2D(new Point2D(550.0, 0.0), new Point2D(580.0, 0)));
		segs.add(new Line2D(new Point2D(570.0, 0.0), new Point2D(800.0, 0)));
		segs.add(new Line2D(new Point2D(850.0, 0.0), new Point2D(900.0, 0)));
		RoadTools.mergeSegments(segs);
		assertTrue(true);
	}
	
}
