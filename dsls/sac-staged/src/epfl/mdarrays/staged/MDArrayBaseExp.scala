package epfl.mdarrays.staged

import _root_.scala.virtualization.lms.common._
import epfl.mdarrays.library.scala._
import epfl.mdarrays.library.scala.Conversions._
import epfl.mdarrays.library.scala.Operations
import epfl.mdarrays.library.scala.MDArrayIO

trait MDArrayBaseExp extends MDArrayBase with EffectExp with /*DeliteIfThenElseExp with*/ ArgumentsExp {
  // needed so that foldTerms are not collected by the CSE
  var foldTermIndex = 0

  //override def readMutableData[A](d: Def[A]) = Nil // TODO: find root cause for blowup
  
  var idxFcd = 0
  override def createDefinition[T](s: Sym[T], d: Def[T]): TP[T] = {
    idxFcd += 1
    val r = super.createDefinition(s,d)
    println("#"+idxFcd + "   " + r)
    r
  }


//  override def syms(e: Any): List[Sym[Any]] = e match {
//    case GenArrayWith(lExpr, shape) => syms(shape) ::: syms(lExpr)
//    case ModArrayWith(lExpr, array) => syms(array) ::: syms(lExpr)
//    case FoldArrayWith(wExpr, neutral, foldTerm1, foldTerm2, foldExpression) =>
//      syms(wExpr) ::: syms(neutral) ::: syms(foldTerm1) ::: syms(foldTerm2) ::: syms(foldExpression)
//    case _ => super.syms(e)
//  }

//  override def syms(e: Any) = e match {
//    case GenArrayWith(lExpr, shape) => syms(shape)
//    case ModArrayWith(lExpr, array) => syms(array)
//    case FoldArrayWith(wExpr, neutral, foldTerm1, foldTerm2, foldExpression) => syms(neutral)
//    case _ => super.syms(e)
//  }

  override def boundSyms(e: Any): List[Sym[Any]] = e match {
    case WithNode(lb, lbStrict, ub, ubStrict, step, width, sym, expr) => sym :: boundSyms(expr)
    case GenArrayWith(lExpr, shape) => lExpr.flatMap(boundSyms(_))
    case ModArrayWith(lExpr, array) => lExpr.flatMap(boundSyms(_))
    case FoldArrayWith(wExpr, neutral, foldTerm1, foldTerm2, foldExpression) => foldTerm1 :: foldTerm2 :: boundSyms(wExpr) ::: boundSyms(foldExpression)
    case _ => super.boundSyms(e)
  }

  override def symsFreq(e: Any): List[(Sym[Any], Double)] = e match {
    case GenArrayWith(lExpr, shape) => freqNormal(shape) ::: lExpr.flatMap(freqCold(_))
    case ModArrayWith(lExpr, array) => freqNormal(array) ::: lExpr.flatMap(freqCold(_))
    case FoldArrayWith(wExpr, neutral, foldTerm1, foldTerm2, foldExpression) =>
      freqCold(wExpr) ::: freqNormal(neutral) ::: freqNormal(foldTerm1) ::: freqNormal(foldTerm2)  ::: freqHot(foldExpression)
    case _ => super.symsFreq(e)
  }

  /*
      Basic AST building blocks
   */
  case class KnownAtRuntime[A: Manifest](name: String) extends Def[MDArray[A]] { override def toString() = "KnownAtRuntime(" + name + ") " }
  case class KnownAtCompileTime[A: Manifest](value: MDArray[A]) extends Def[MDArray[A]] {override def toString() = "KnownAtCompileTime(" + value.toString + ")" }

  // Conversions within the staged universe
  case class FromMDArrayList[A: Manifest](list: List[Exp[MDArray[A]]]) extends Def[MDArray[A]] { override def toString() = "FromMDArrayList(" + list.toString + ")" }
  case class FromList[A: Manifest](value: Exp[List[A]]) extends Def[MDArray[A]] { override def toString() = "FromList(" + value.toString + ")" }
  case class FromArray[A: Manifest](value: Exp[Array[A]]) extends Def[MDArray[A]] { override def toString() = "FromArray(" + value.toString + ")" }
  case class FromValue[A: Manifest](value: Exp[A]) extends Def[MDArray[A]] { override def toString() = "FromValue(" + value.toString + ")" }

  // Going back to the real world
  case class ToList[A: Manifest](value: Exp[MDArray[A]]) extends Def[List[A]] { override def toString() = "ToList(" + value.toString + ")" }
  case class ToArray[A: Manifest](value: Exp[MDArray[A]]) extends Def[Array[A]] { override def toString() = "ToArray(" + value.toString + ")" }
  case class ToValue[A: Manifest](value: Exp[MDArray[A]]) extends Def[A] { override def toString() = "ToValue(" + value.toString + ")" }
  case class ToString[A: Manifest](value: Exp[MDArray[A]]) extends Def[String]

  def arrayFromValue[A: Manifest](value: Exp[A]): Exp[MDArray[A]] = value match {
    case Const(v) => KnownAtCompileTime(v)
    case Def(ToValue(v)) => v
    case _ => FromValue(value)
  }

  def arrayToValue[A: Manifest](value: Exp[MDArray[A]]): Exp[A] = value match {
    case Def(KnownAtCompileTime(v)) => Const(v.content()(0)) // TODO: should assert dim == 0 ??
    case Def(FromValue(v)) => v
    case _ => ToValue(value)
  }


  // With
  case class WithNode[A: Manifest](lb: Exp[MDArray[Int]], lbStrict: Exp[Boolean], ub: Exp[MDArray[Int]], ubStrict: Exp[Boolean], step: Exp[MDArray[Int]], width: Exp[MDArray[Int]], sym: Sym[MDArray[Int]], expr: Exp[MDArray[A]]) extends Def[MDArray[A]] {
    override def toString() = "With(lb=" + lb.toString + " lbStrict=" + lbStrict.toString + " ubStict=" + ubStrict.toString + " ub=" + ub.toString + " step=" + step.toString + " width=" + width.toString + "  " + sym.toString + " => " + expr.toString + ")"
  }
  case class GenArrayWith[A: Manifest](lExpr: List[Exp[MDArray[A]]], shp: Exp[MDArray[Int]]) extends Def[MDArray[A]] { override def toString() = "GenArrayWith(" + shp.toString + " - " + lExpr.mkString(", ") + ")" }
  case class ModArrayWith[A: Manifest](lExpr: List[Exp[MDArray[A]]], a: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "ModArrayWith(" + a.toString + " - " + lExpr.mkString(", ") + ")" }
  // note:     Important implicit assumption made here -- we assume foldFunction has no outside dependencies. According to the SAC spec, it should indeed be the case, but proving it would be better
  // response: It's the programmer's responsibility to ensure the fold function is associative and has no outside dependencies
  // TODO: When folding with loops, Fold will have to include intervals
  case class FoldArrayWith[A: Manifest](wExpr: Exp[MDArray[A]], neutral: Exp[MDArray[A]], foldTerm1: Sym[MDArray[A]], foldTerm2: Sym[MDArray[A]], foldExpression: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "FoldArrayWith(" + neutral + ", fold (" + foldTerm1 + ", " + foldTerm2 + ") => " + foldExpression + ", " + wExpr + ")" }

  // Base functions
  case class ToDim[A: Manifest](a: Exp[MDArray[A]]) extends Def[Int] { override def toString() = "Dim(" + a + ")" }
  case class ToShape[A: Manifest](a: Exp[MDArray[A]]) extends Def[MDArray[Int]] { override def toString() = "Shape(" + a + ")" }
  case class Reshape[A: Manifest](shp: Exp[MDArray[Int]], a: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "Reshape(" + shp + ", " + a + ")" }
  case class Sel[A: Manifest](iv: Exp[MDArray[Int]], a: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "Sel(" + iv + ", " + a + ")" }
  case class Cat[A: Manifest](d: Exp[MDArray[Int]], a: Exp[MDArray[A]], b: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "Cat(" + d + ", " + a + ", " + b + ")" }

  // Operations and Values
  // XXX: In an optimization phase, operations and values will end up as With loops => the only reason to keep them
  // as independent operations are to have more powerful constraints in the typing system
  case class InfixOp[A: Manifest, B: Manifest](array1: Exp[MDArray[A]], array2: Exp[MDArray[A]], op: (A, A) => B, opName: String) extends Def[MDArray[B]] { override def toString() = "InfixOp(" + opName + ": " + array1 + " and " + array2 + ")" }
  case class UnaryOp[A: Manifest, B: Manifest](array: Exp[MDArray[A]], op: A => B, opName: String) extends Def[MDArray[B]] { override def toString() = "UnaryOp(" + opName + ": " + array + ")" }
  case class Where[A: Manifest](cond: Exp[MDArray[Boolean]], array1: Exp[MDArray[A]], array2: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "Where(" + cond + ", " + array1 + ", " + array2 + ")" }
  case class Values[A: Manifest](dim: Exp[MDArray[Int]], value: Exp[MDArray[A]]) extends Def[MDArray[A]] { override def toString() = "Values(" + value + ", " + dim + ")" }

  // Nothing (a replacement for the null)
  case object Nothing extends Def[MDArray[Int]] { override def toString() = "<nothing>" }

  // Function wrapping
  case class ScalarOperatorWrapper[A: Manifest, B: Manifest, C: Manifest](f: (A,B)=>C, operator: String) extends ((Exp[MDArray[A]], Exp[MDArray[B]]) => Exp[MDArray[C]]) {
    def apply(a: Exp[MDArray[A]], b: Exp[MDArray[B]]): Exp[MDArray[C]] = ScalarOperatorApplication[A,B,C](/*f, */operator, a, b)
  }
  case class ScalarOperatorApplication[A, B, C](/*f: (A,B)=>C, */operator: String, a: Exp[MDArray[A]], b: Exp[MDArray[B]])(implicit mfA: Manifest[A], mfB: Manifest[B], mfC: Manifest[C]) extends Def[MDArray[C]] {
    def getMfA = manifest[A]
    def getMfB = manifest[B]
    def getMfC = manifest[C]
    override def toString() = "ScalarOperator " + a + " " + operator + " " + b
  }

  // IO Nodes
  case class ReadMDArray[A: Manifest](fileName: Rep[MDArray[String]], jitShape: Option[MDArray[Int]]) extends Def[MDArray[A]] { override def toString = "ReadMDArray(" + fileName + ")"; def getManifest = manifest[A] }
  case class WriteMDArray[A: Manifest](fileName: Rep[MDArray[String]], array: Rep[MDArray[A]]) extends Def[Unit] { override def toString = "WriteMDArray(" + fileName + ", " + array + ")"; def getManifest = manifest[A] }

  // Timer nodes
  case class StartTimer(afterComputing: List[Rep[Any]]) extends Def[Unit]
  case class StopTimer(afterComputing: List[Rep[Any]]) extends Def[Unit]

  /*
      Abstract function implementation
   */
  // Implicit conversions
  implicit def convertFromListRep[A: Manifest](a: List[A]): Exp[MDArray[A]] = KnownAtCompileTime(a)
  implicit def convertFromArrayRep[A: Manifest](a: Array[A]): Exp[MDArray[A]] = KnownAtCompileTime(a)
  implicit def convertFromValueRep[A: Manifest](a: A): Exp[MDArray[A]] = KnownAtCompileTime(a)

  // Implicit conversions from unknown elements
  implicit def convertFromListRepRep[A: Manifest](a: Exp[List[A]]): Exp[MDArray[A]] = FromList(a)
  implicit def convertFromArrayRepRep[A: Manifest](a: Exp[Array[A]]): Exp[MDArray[A]] = FromArray(a)
  implicit def convertFromValueRepRep[A: Manifest](a: Exp[A]): Exp[MDArray[A]] = arrayFromValue(a)
  implicit def convertFromMDArrayList[A: Manifest](a: List[Exp[MDArray[A]]]): Exp[MDArray[A]] = FromMDArrayList(a)

  // Implicit conversion from MDArray to staged array
  implicit def convertFromMDArrayToRep[A: Manifest](a: MDArray[A]): Exp[MDArray[A]] = KnownAtCompileTime(a)

  implicit def convertToListRep[A: Manifest](a: Exp[MDArray[A]]): Exp[List[A]] = ToList(a)
  implicit def convertToArrayRep[A: Manifest](a: Exp[MDArray[A]]): Exp[Array[A]] = ToArray(a)
  implicit def convertToValueRep[A: Manifest](a: Exp[MDArray[A]]): Exp[A] = arrayToValue(a)

  // Explicit conversion for elements known only at runtime, including shape
  // To create an element with known shape, use reshape(<known shape>, knownOnlyAtRuntime(...))
  def knownOnlyAtRuntime[A](name: String)(implicit mf: Manifest[A]): Exp[MDArray[A]] = KnownAtRuntime[A](name)

  // Basic operations
  def dim[A: Manifest](a: Exp[MDArray[A]]): Exp[Int] = a match {
    case Def(KnownAtCompileTime(b)) => Const(b.dim)
    case _ => ToDim(a) // TODO: look for compile time shape
  }
  
  
  var ivShapeRelation: List[(Sym[MDArray[_]], Exp[MDArray[Int]])] = Nil
  
  override def reset = {
    super.reset
    ivShapeRelation = Nil
  }
  
  
  def shape[A: Manifest](a: Exp[MDArray[A]]): Exp[MDArray[Int]] = a match {
    case Def(Reflect(ReadMDArray(_, Some(hint)), _, _)) => KnownAtCompileTime(hint)
    case Def(KnownAtCompileTime(b)) => KnownAtCompileTime(b.shape)
    case s: Sym[_] => 
      // if this is a loop iv, it's shape is equal to the shape of the loop bounds
      //val withDef = globalDefs.collectFirst {
      //  case TP(_, WithNode(lb, lbStrict, ub, ubStrict, step, width, sym, expr)) if sym == s => shape(lb)
      //}
      ivShapeRelation.collectFirst { case (`s`, shp) => shp }.getOrElse(ToShape(a))
    case _ => ToShape(a)
  }
  
  def sel[A: Manifest](iv: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] = (iv,a) match {
    case (Def(KnownAtCompileTime(iw)), Def(KnownAtCompileTime(b))) => 
      KnownAtCompileTime(b.sel(iw))
    case (Def(KnownAtCompileTime(iw)), _) => 
      println("sel with known iv " + iv +"="+iw+ " shape " + shape(a))
      shape(a) match {
        case Def(KnownAtCompileTime(sa)) =>
          println("select " + a + " with shape " + sa + " at " + iw)
          
          // TODO: could optimize selection ...
                    
/*        case Def(KnownAtCompileTime(sa)) if sa.dim == b.dim =>
          println("sel with known iv and known shape " + iv)
*/          
          Sel(iv, a)
        case _ => Sel(iv, a)
      }
    
    case _ => Sel(iv, a)
  }
  
  def reshape[A: Manifest](iv: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] = Reshape(iv, a)
  def cat[A: Manifest](d: Rep[MDArray[Int]], one: Exp[MDArray[A]], two: Exp[MDArray[A]]): Exp[MDArray[A]] = Cat(d, one, two)

  // Zeroes, ones and values
  def values(dim: Exp[MDArray[Int]], value: Exp[MDArray[Int]]): Exp[MDArray[Int]] = (dim,value) match {
    case (Def(KnownAtCompileTime(d)), Def(KnownAtCompileTime(v))) =>
      // TODO: maybe we don't want to create the full array at compile time but just its shape
      val shp = Array(d.content()(0))
      val dta = new Array[Int](d.content()(0))
      var i = 0
      while (i < dta.length) {
        dta(i) = v.content()(0)
        i += 1
      }
      KnownAtCompileTime(new MDArray[Int](shp,dta))
    case _ =>
      // XXX: Let's make values a primitive, before with loops
      // With().GenArray(convertFromValueRepRep(dim), iv => value)
      Values(dim, value)
  }

  // Where
  def where[A: Manifest](p: Exp[MDArray[Boolean]], a: Exp[MDArray[A]], b: Exp[MDArray[A]]): Exp[MDArray[A]] = Where(p, a, b)

  // Restructuring operations - implemented as with-loops
  def genarray[A: Manifest](shp: Exp[MDArray[Int]], value: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(function = iv => value).GenArray(shp)
  def modarray[A: Manifest](a: Exp[MDArray[A]], iv: Exp[MDArray[Int]], value: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(lb = iv, ub = iv, function = iv => value).ModArray(a)

  // TODO: Redesign these functions for lower dimensions in the given vectors, filling in with zeros or shape elements
  // TODO: Decide if this hasn't been already encoded by the use of with loops
  def take[A: Manifest](shp: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(function = iv => sel(iv, a)).GenArray(shp)
  def drop[A: Manifest](shp: Exp[MDArray[Int]], a: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(function = iv => sel(iv + shp, a)).GenArray(shape(a) - shp)
  def tile[A: Manifest](sv: Exp[MDArray[Int]], ov: Exp[MDArray[Int]], a:Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(function = iv => sel(iv + ov, a)).GenArray(sv)
  def rotate[A: Manifest](ov: Exp[MDArray[Int]], a:Exp[MDArray[A]]): Exp[MDArray[A]] =
    With(function = iv => a(((iv - ov) + shape(a)) % shape(a))).GenArray(shape(a))
  def shift[A: Manifest](ov: Exp[MDArray[Int]], expr: Exp[MDArray[A]], a: Exp[MDArray[A]]): Exp[MDArray[A]] =
    With[A](function = iv => if ((any((iv - ov) < zeros(dim(a)))) || (any((iv - ov) >= shape(a)))) expr else a(iv - ov)).GenArray(shape(a))

  // Reduction operations on matrices
  def sum[A](a: Exp[MDArray[A]])(implicit ev: Numeric[A], mf: Manifest[A]): Exp[A] = reduce[A](ev.zero, a, scalarOperationWrapper[A,A,A]((a: A, b: A) => ev.plus(a, b), "+"), "sum")
  def prod[A](a: Exp[MDArray[A]])(implicit ev: Numeric[A], mf: Manifest[A]): Exp[A] = reduce[A](ev.one, a, scalarOperationWrapper[A,A,A]((a: A, b: A) => ev.times(a, b), "*"), "prod")
  def all(a: Exp[MDArray[Boolean]]): Exp[Boolean] = reduce[Boolean](true, a, scalarOperationWrapper[Boolean,Boolean,Boolean]((x: Boolean, y: Boolean) => x && y, "&&"), "all")
  def any(a: Exp[MDArray[Boolean]]): Exp[Boolean] = reduce[Boolean](false, a, scalarOperationWrapper[Boolean,Boolean,Boolean]((x: Boolean, y: Boolean) => x || y, "||"), "any")
  def maxVal[A](a: Exp[MDArray[A]])(implicit ev: Ordering[A], mf: Manifest[A], mfb: Manifest[Boolean]): Exp[A] = reduce[A](sel(zeros(dim(a)),a), a, (a: Exp[MDArray[A]], b: Exp[MDArray[A]]) => if (scalarOperationWrapper[A, A, Boolean]((a, b) => ev.gt(a,b), ">")(mf, mf, mfb)(a, b)) a else b, "max")
  def minVal[A](a: Exp[MDArray[A]])(implicit ev: Ordering[A], mf: Manifest[A], mfb: Manifest[Boolean]): Exp[A] = reduce[A](sel(zeros(dim(a)),a), a, (a: Exp[MDArray[A]], b: Exp[MDArray[A]]) => if (scalarOperationWrapper[A, A, Boolean]((a, b) => ev.lt(a,b), "<")(mf, mf, mfb)(a, b)) a else b, "min")

  // Basic operations on matrices - they appear as private here
  def op[A, B](a:Exp[MDArray[A]], b:Exp[MDArray[A]])(op: (A, A) => B, opName: String)(implicit mfA: Manifest[A], mfB: Manifest[B]): Exp[MDArray[B]] =
    InfixOp(a, b, op, opName)
  def uop[A, B](a:Exp[MDArray[A]])(op: A => B, opName: String)(implicit mfA: Manifest[A], mfB: Manifest[B]): Exp[MDArray[B]] =
    UnaryOp(a, op, opName)
  def reduce[A](z: Exp[MDArray[A]], a: Exp[MDArray[A]], op: (Exp[MDArray[A]], Exp[MDArray[A]]) => Exp[MDArray[A]], opName: String)(implicit mfA: Manifest[A]): Exp[MDArray[A]] =
    With[A](lb = zeros(dim(a)), lbStrict = false, ubStrict = true, ub = shape(a), function = (iv) => sel(iv, a)).Fold(op, z)

  // With-comprehensions
  def toWithNode[A: Manifest](withObject: With[A]): Exp[MDArray[A]] = {
    val sym: Sym[MDArray[Int]] = fresh[MDArray[Int]]
    ivShapeRelation = (sym, shape(withObject.lb))::ivShapeRelation
    WithNode(withObject.lb, withObject.lbStrict, withObject.ub, withObject.ubStrict, withObject.step, withObject.width, sym, withObject.function(sym))
  }

  def genArrayWith[A: Manifest](w: With[A], shp: Exp[MDArray[Int]]): Exp[MDArray[A]] = GenArrayWith(toWithNode(w)::Nil, shp)
  def modArrayWith[A: Manifest](w: With[A], a: Exp[MDArray[A]]): Exp[MDArray[A]] = ModArrayWith(toWithNode(w)::Nil, a)
  def foldArrayWith[A: Manifest](w: With[A], foldFunction: (Exp[MDArray[A]], Exp[MDArray[A]]) => Exp[MDArray[A]], neutral: Exp[MDArray[A]]): Exp[MDArray[A]] = {
    val foldTerm1 = fresh[MDArray[A]]
    val foldTerm2 = fresh[MDArray[A]]
    val foldExpression = foldFunction(foldTerm1, foldTerm2)
    FoldArrayWith(toWithNode(w), neutral, foldTerm1, foldTerm2, foldExpression)
  }

  // ToString
  def doToString[A: Manifest](a: Exp[MDArray[A]]) = ToString(a)

  // Function wrapping for scalar elements to mdarrays
  def scalarOperationWrapper[A: Manifest, B: Manifest, C: Manifest](f: (A,B)=>C, operator: String): ((Exp[MDArray[A]], Exp[MDArray[B]]) => Exp[MDArray[C]]) = ScalarOperatorWrapper(f, operator)

  // The IO functions
  def readMDArray[A: Manifest](fileName: Exp[MDArray[String]], jit: Boolean = true): Exp[MDArray[A]] = jit match {
    case true =>
      var hint: MDArray[Int] = null

      // TODO: If it's that complicated, it's broken! Fixme
      if (fileName.isInstanceOf[Sym[_]])
        findDefinition(fileName.asInstanceOf[Sym[_]]) match {
          case Some(tp) =>
            if (tp.rhs.isInstanceOf[KnownAtCompileTime[_]]) {
              val kc = tp.rhs.asInstanceOf[KnownAtCompileTime[Any]]
              val value = kc.value.asInstanceOf[MDArray[String]]
              if (Operations.dim(value) == 0)
                hint = MDArrayIO.readMDArrayShape(value.content()(0))
            }
          case None =>
        }

      hint match {
        case null =>
          val sym: Exp[MDArray[A]] = reflectEffect(ReadMDArray(fileName, None))
          println("Warning: Using JIT compilation for " + sym + ": Cannot access file denoted by: " + findDefinition(fileName.asInstanceOf[Sym[_]]))
          sym
        case _ =>
          val sym: Exp[MDArray[A]] = reflectEffect(ReadMDArray(fileName, Some(hint)))
          println("Warning: Using JIT compilation for " + sym + " hint: " + hint)
          sym
      }
    case _ =>
      // the ahead-of-time case
      reflectEffect(ReadMDArray(fileName, None))
  }

  def writeMDArray[A: Manifest](fileName: Exp[MDArray[String]], array: Exp[MDArray[A]]): Exp[Unit] =
    reflectEffect(WriteMDArray(fileName, array))

  // Timer functions
  def startTimer(afterComputing: List[Rep[Any]]): Rep[Unit] = reflectEffect(StartTimer(afterComputing))
  def stopTimer(afterComputing: List[Rep[Any]]): Rep[Unit] = reflectEffect(StopTimer(afterComputing))

  protected val nothing: Exp[MDArray[Int]] = Nothing
}
