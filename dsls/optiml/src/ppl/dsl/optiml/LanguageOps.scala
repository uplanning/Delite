package ppl.dsl.optiml

import datastruct.scala._
import ppl.delite.framework.ops.DeliteOpsExp
import java.io.PrintWriter
import reflect.Manifest
import scala.virtualization.lms.internal.GenericFatCodegen
import scala.virtualization.lms.common._

/* Machinery provided by OptiML itself (language features and control structures).
 *
 * author: Arvind Sujeeth (asujeeth@stanford.edu)
 * created: Nov 29, 2010
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 *
 */

trait LanguageOps extends Base { this: OptiML =>

  /**
   * random
   */

  // this version is for optiml's use exclusively, so it does not interfere with application behavior
  private def _random[A](implicit mA: Manifest[A]): Rep[A] =
    mA match {
      case Manifest.Double => optiml_internal_rand_double.asInstanceOfL[A]
      case Manifest.Float => optiml_internal_rand_float.asInstanceOfL[A]
      case Manifest.Int => optiml_internal_rand_int.asInstanceOfL[A]
      case Manifest.Long => optiml_internal_rand_long.asInstanceOfL[A]
      case Manifest.Boolean => optiml_internal_rand_boolean.asInstanceOfL[A]
      case _ => throw new UnsupportedOperationException()
  }

  // public version for application use
  def random[A](implicit mA: Manifest[A]): Rep[A] =
    mA match {
      case Manifest.Double => optiml_rand_double.asInstanceOfL[A]
      case Manifest.Float => optiml_rand_float.asInstanceOfL[A]
      case Manifest.Int => optiml_rand_int.asInstanceOfL[A]
      case Manifest.Long => optiml_rand_long.asInstanceOfL[A]
      case Manifest.Boolean => optiml_rand_boolean.asInstanceOfL[A]
      case _ => throw new UnsupportedOperationException()
  }

  def randomGaussian = optiml_rand_gaussian

  def reseed {
    // reseeds for all threads
    optiml_reseed()
  }

  def optiml_internal_rand_double(): Rep[Double]
  def optiml_internal_rand_float(): Rep[Float]
  def optiml_internal_rand_int(): Rep[Int]
  def optiml_internal_rand_long(): Rep[Long]
  def optiml_internal_rand_boolean(): Rep[Boolean]

  def optiml_rand_double(): Rep[Double]
  def optiml_rand_float(): Rep[Float]
  def optiml_rand_int(): Rep[Int]
  def optiml_rand_long(): Rep[Long]
  def optiml_rand_boolean(): Rep[Boolean]
  def optiml_rand_gaussian(): Rep[Double]

  def optiml_reseed(): Rep[Unit]

  /**
   * Zero
   */
  // TODO: type class should probable be Zeroable[A] or something
  //def <>[A:Manifest:Arith] = optiml_zero

  // TODO: for some reason, the implicit ops conversions aren't kicking in for sum/min/max

  /**
   * sum
   */
  def sum[A:Manifest:Arith](start: Rep[Int], end: Rep[Int])(block: Rep[Int] => Rep[A]) = optiml_sum(start, end, block)
  def sum[A:Manifest:Arith](vals: Rep[Vector[A]]) = repVecToVecOps(vals).sum
  def sum[A](vals: Rep[Matrix[A]])(implicit mA: Manifest[A], a: Arith[A], o: Overloaded1) = repMatToMatOps(vals).sum

  def optiml_sum[A:Manifest:Arith](start: Rep[Int], end: Rep[Int], block: Rep[Int] => Rep[A]) : Rep[A]


  /**
   * min
   */
  def min[A:Manifest:Ordering](vals: Rep[Vector[A]]) = repVecToVecOps(vals).min
  def min[A](vals: Rep[Matrix[A]])(implicit mA: Manifest[A], ord: Ordering[A], o: Overloaded1) = repMatToMatOps(vals).min
  def min[A:Manifest:Ordering](vals: A*) = repVecToVecOps(Vector(vals: _*)).min


  /**
   * max
   */
  def max[A:Manifest:Ordering](vals: Rep[Vector[A]]) = repVecToVecOps(vals).max
  def max[A](vals: Rep[Matrix[A]])(implicit mA: Manifest[A], ord: Ordering[A], o: Overloaded1) = repMatToMatOps(vals).max
  def max[A:Manifest:Ordering](vals: A*) = repVecToVecOps(Vector(vals: _*)).max
  //def max[A:Manifest:Ordering](vals: Rep[A]*) = repVecToVecOps(Vector(vals: _*)).max


  /**
   * mean
   * TODO: implement this in vector/matrix
   */


  /**
   * abs
   */
  // TODO: sbt fails without the explicit invocation of arithToArithOps, but IDEA compiles. wtf?
  def abs[A:Manifest:Arith](elem: Rep[A]) = repArithToArithOps(elem).abs
  //def abs[A](vals: Rep[Vector[A]])(implicit mA: Manifest[A], a: Arith[A], o: Overloaded1) = vals.abs
  //def abs[A](vals: Rep[Matrix[A]])(implicit mA: Manifest[A], a: Arith[A], o: Overloaded2) = vals.abs


  /**
   *  IndexVector construction
   */
  implicit def intToIndexOp(i : Int) = new IndexOp(i)
  implicit def repIntToIndexOp(i : Rep[Int]) = new IndexOp(i)

  class IndexOp(val _end : Rep[Int]) {
    def ::(_start : Rep[Int]) = indexvector_range(_start, _end)
  }

  /**
   * IndexVector2 construction (Matrix construction)
   * ex.  (0::n, *) { { rowIdx => ... }
   * ex2. (*, 0::n) { { colIdx => ... }
   * ex3. (0::n,0::m) { (i,j) => .. }
   */
  class IndexWildcard
  val * = new IndexWildcard

  implicit def tuple2ToIndexVector1(tup: (Rep[IndexVector], IndexWildcard))(implicit overloaded1 : Overloaded1) = indexvector2_new(tup._1, indexvector2_wildcard())
  implicit def tuple2ToIndexVector2(tup: (IndexWildcard, Rep[IndexVector]))(implicit overloaded2 : Overloaded2) = indexvector2_new(indexvector2_wildcard(), tup._2)
  implicit def tuple2ToIndexVector3(tup: (Rep[IndexVector], Rep[IndexVector]))(implicit overloaded3 : Overloaded3) = indexvector2_new(tup._1, tup._2)

  /**
   * untilconverged
   */
  def untilconverged[A](x: Rep[A],
                        thresh: Rep[Double],
                        max_iter: Rep[Int] = unit(1000),
                        clone_prev_val: Rep[Boolean] = unit(true))
                        (block: Rep[A] => Rep[A])
                        (implicit diff: (Rep[A],Rep[A]) => Rep[Double], mA: Manifest[A], c: Cloneable[A]): Rep[A]
    = optiml_untilconverged(x, thresh, max_iter, clone_prev_val, block, diff)


  def optiml_untilconverged[A:Manifest:Cloneable](x: Rep[A], thresh: Rep[Double], max_iter: Rep[Int], clone_prev_val: Rep[Boolean],
                                                  block: Rep[A] => Rep[A], diff: (Rep[A],Rep[A]) => Rep[Double]): Rep[A]

  def untilconverged[V <: Vertex, E <: Edge](g: Rep[Graph[V, E]])
                        (block: Rep[V] => Rep[Unit])
                        (implicit mV: Manifest[V], mE: Manifest[E]): Rep[Unit]
    = optiml_untilconverged(g, block)

  def optiml_untilconverged[V <: Vertex :Manifest, E <: Edge :Manifest](g: Rep[Graph[V, E]], block: Rep[V] => Rep[Unit]) : Rep[Unit]


  /**
   *  input parsing
   */
  def loadMatrix(filename: Rep[String]) = MLInputReader.read(filename)
  def loadVector(filename: Rep[String]) = MLInputReader.readVector(filename)
  def loadImage(filename: Rep[String]) = MLInputReader.readGrayscaleImage(filename)

  // distance metrics
  val ABS = 0
  val EUC = 1
  val SQUARE = 2

  implicit val vecDiff : (Rep[Vector[Double]], Rep[Vector[Double]]) => Rep[Double] = (v1,v2) => dist(v1,v2)
  implicit val matDiff : (Rep[Matrix[Double]], Rep[Matrix[Double]]) => Rep[Double] = (m1,m2) => dist(m1,m2)

  // in 2.9, multiple overloaded values cannot all define default arguments
  def dist[A:Manifest:Arith](v1: Rep[Vector[A]], v2: Rep[Vector[A]]) = {
    optiml_vector_dist(v1,v2,ABS)
  }

  def dist[A:Manifest:Arith](v1: Rep[Vector[A]], v2: Rep[Vector[A]], metric: Rep[Int]) = {
    optiml_vector_dist(v1,v2,metric)
  }

  def dist[A](m1: Rep[Matrix[A]], m2: Rep[Matrix[A]])
             (implicit mA: Manifest[A], a: Arith[A], o: Overloaded1) = {
    optiml_matrix_dist(m1,m2,ABS)
  }
 
  def dist[A](m1: Rep[Matrix[A]], m2: Rep[Matrix[A]], metric: Rep[Int])
             (implicit mA: Manifest[A], a: Arith[A], o: Overloaded1) = {
    optiml_matrix_dist(m1,m2,metric)
  }

  def optiml_vector_dist[A:Manifest:Arith](v1: Rep[Vector[A]], v2: Rep[Vector[A]], metric: Rep[Int]): Rep[A]
  def optiml_matrix_dist[A:Manifest:Arith](m1: Rep[Matrix[A]], m2: Rep[Matrix[A]], metric: Rep[Int]): Rep[A]

  /**
   *   Profiling
   */
  // lightweight profiling, matlab style
  def tic() = profile_start()
  def toc() = profile_stop()

  def profile_start() : Rep[Unit]
  def profile_stop() : Rep[Unit]
}

trait LanguageOpsExp extends LanguageOps with BaseFatExp with EffectExp {
  this: OptiMLExp with LanguageImplOps =>

  case class InternalRandDouble() extends Def[Double]
  case class InternalRandFloat() extends Def[Float]
  case class InternalRandInt() extends Def[Int]
  case class InternalRandLong() extends Def[Long]
  case class InternalRandBoolean() extends Def[Boolean]

  case class RandDouble() extends Def[Double]
  case class RandFloat() extends Def[Float]
  case class RandInt() extends Def[Int]
  case class RandLong() extends Def[Long]
  case class RandBoolean() extends Def[Boolean]

  case class RandGaussian() extends Def[Double]

  case class RandReseed() extends Def[Unit]


  /**
   * Random
   */
  def optiml_internal_rand_double() = reflectEffect(InternalRandDouble())
  def optiml_internal_rand_float() = reflectEffect(InternalRandFloat())
  def optiml_internal_rand_int() = reflectEffect(InternalRandInt())
  def optiml_internal_rand_long() = reflectEffect(InternalRandLong())
  def optiml_internal_rand_boolean() = reflectEffect(InternalRandBoolean())

  def optiml_rand_double() = reflectEffect(RandDouble())
  def optiml_rand_float() = reflectEffect(RandFloat())
  def optiml_rand_int() = reflectEffect(RandInt())
  def optiml_rand_long() = reflectEffect(RandLong())
  def optiml_rand_boolean() = reflectEffect(RandBoolean())
  def optiml_rand_gaussian() = reflectEffect(RandGaussian())

  def optiml_reseed() = reflectEffect(RandReseed())


  /**
   * Sum
   */

  case class Sum[A:Manifest:Arith](start: Exp[Int], end: Exp[Int], mV: Sym[Int], map: Exp[A])
    extends DeliteOpMapReduce[Int,A,Vector] {

    val in = Vector.range(start, end)
    val rV = (fresh[A],fresh[A])
    val reduce = reifyEffects(rV._1 += rV._2) //TODO TR write to non-mutable
    //val mapreduce = reifyEffects(ops.+=(acc, reifyEffects(block(mV))))
  }

  def optiml_sum[A:Manifest:Arith](start: Exp[Int], end: Exp[Int], block: Exp[Int] => Exp[A]) = {

    val mV = fresh[Int]
    val map = reifyEffects(block(mV))
    // reflectEffect should not be necessary -- see IndexVectorConstruct for explanation
    reflectEffect(Sum(start, end, mV, map))
  }


  /**
   * untilconverged
   */
  
//  case class UntilConverged[A:Manifest:Cloneable](x: Exp[A], thresh: Exp[Double], max_iter: Exp[Int], clone_prev_val: Exp[Boolean],
//                                                  func: Exp[A] => Exp[A], diff: (Exp[A],Exp[A]) => Exp[Double])
//    extends DeliteOpSingleTask[A](reifyEffects(optiml_untilconverged_impl(x,thresh,max_iter,clone_prev_val,func,diff)))
//

//  def optiml_untilconverged[A:Manifest:Cloneable](x: Exp[A], thresh: Exp[Double], max_iter: Exp[Int], clone_prev_val: Exp[Boolean],
//                                                  block: Exp[A] => Exp[A], diff: (Exp[A],Exp[A]) => Exp[Double]) =
//    reflectEffect(UntilConverged(x, thresh, max_iter, clone_prev_val, block, diff))
//

  // for now, just unroll the implementation
  // we need a concept of a composite op to do this without unrolling, so that we can have a different result type than the while
  def optiml_untilconverged[V <: Vertex : Manifest, E <: Edge : Manifest](g: Rep[Graph[V, E]], block: Rep[V] => Rep[Unit]) = {
    val vertices = g.vertices

    val tasks = vertices.cloneL
    val seen = Set[V]()
    
    while(tasks.length > 0) {
      tasks.foreach(block)
      tasks.clear()
      var totalTasks = unit(0)
      
      for(i <- 0 until vertices.length) {
        val vtasks = vertices(i).tasks
        totalTasks += vtasks.length
        for(j <- 0 until vtasks.length) {
          val task = vtasks(j).asInstanceOfL[V]
          if(!seen.contains(task)) {
            tasks += task   //TODO TR: non-mutable write (use mclone)
            seen.add(task)   //TODO TR: non-mutable write
          }
        }

        vertices(i).clearTasks() //TODO TR: non-mutable write
      }

      //println("tasks: " + tasks.length)
      seen.clear()
    }
  }

  def optiml_untilconverged[A:Manifest:Cloneable](x: Exp[A], thresh: Exp[Double], max_iter: Exp[Int], clone_prev_val: Exp[Boolean],
                                                  block: Exp[A] => Exp[A], diff: (Exp[A],Exp[A]) => Exp[Double]) = {

    var delta = unit(scala.Math.MAX_DOUBLE)
    var prev = unit(null).asInstanceOfL[A]
    var next = x
    var iter = unit(0)

    while ((Math.abs(delta) > thresh) && (iter < max_iter)){
      if (clone_prev_val)
        prev = next.cloneL()
      else
        prev = next

//      try{
        next = block(next)
//      }
//      catch{
//        case e: Exception => throw new ConvergenceException("Converging block threw exception: " + e)
//      }
      iter += 1
      delta = diff(next, prev)
      //println("(" + delta + ")")
    }

      if (iter == max_iter){
        //throw new ConvergenceException("Maximum iterations exceeded")
        println(unit("Maximum iterations exceeded"))
        returnL()
      }

    next

  }

  /**
   * dist
   */

  case class VectorDistance[A:Manifest:Arith](v1: Rep[Vector[A]], v2: Rep[Vector[A]], metric: Rep[Int])
    extends DeliteOpSingleTask[A](reifyEffects(optiml_vectordistance_impl(v1,v2,metric)))

  case class MatrixDistance[A:Manifest:Arith](m1: Rep[Matrix[A]], m2: Rep[Matrix[A]], metric: Rep[Int])
    extends DeliteOpSingleTask[A](reifyEffects(optiml_matrixdistance_impl(m1,m2,metric)))

  def optiml_vector_dist[A:Manifest:Arith](v1: Rep[Vector[A]], v2: Rep[Vector[A]], metric: Rep[Int]) = VectorDistance(v1,v2,metric)
  def optiml_matrix_dist[A:Manifest:Arith](m1: Rep[Matrix[A]], m2: Rep[Matrix[A]], metric: Rep[Int]) = MatrixDistance(m1,m2,metric)

  /**
   * Profiling
   */
  case class ProfileStart() extends Def[Unit]
  case class ProfileStop() extends Def[Unit]

  def profile_start() = reflectEffect(ProfileStart())
  def profile_stop() = reflectEffect(ProfileStop())
}

trait BaseGenLanguageOps extends GenericFatCodegen {
  val IR: LanguageOpsExp
  import IR._

  /*
  override def syms(e: Any): List[Sym[Any]] = e match {
    case _ => super.syms(e)
  }

  override def getFreeVarNode(rhs: Def[Any]): List[Sym[Any]] = rhs match {
    case _ => super.getFreeVarNode(rhs)
  }
  */
}

trait ScalaGenLanguageOps extends ScalaGenEffect with BaseGenLanguageOps {
  val IR: LanguageOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = {
    rhs match {
      case InternalRandDouble() => emitValDef(sym, "generated.scala.Global.intRandRef.nextDouble()")
      case InternalRandFloat() => emitValDef(sym, "generated.scala.Global.intRandRef.nextFloat()")
      case InternalRandInt() => emitValDef(sym, "generated.scala.Global.intRandRef.nextInt()")
      case InternalRandLong() => emitValDef(sym, "generated.scala.Global.intRandRef.nextLong()")
      case InternalRandBoolean() => emitValDef(sym, "generated.scala.Global.intRandRef.nextBoolean()")
      case RandDouble() => emitValDef(sym, "generated.scala.Global.randRef.nextDouble()")
      case RandFloat() => emitValDef(sym, "generated.scala.Global.randRef.nextFloat()")
      case RandInt() => emitValDef(sym, "generated.scala.Global.randRef.nextInt()")
      case RandLong() => emitValDef(sym, "generated.scala.Global.randRef.nextLong()")
      case RandBoolean() => emitValDef(sym, "generated.scala.Global.randRef.nextBoolean()")
      case RandGaussian() => emitValDef(sym, "generated.scala.Global.randRef.nextGaussian()")
      case RandReseed() => emitValDef(sym, "{ generated.scala.Global.randRef.setSeed(generated.scala.Global.INITIAL_SEED);" +
                                           "   generated.scala.Global.intRandRef.setSeed(generated.scala.Global.INITIAL_SEED); }")
      case ProfileStart() => emitValDef(sym, "ppl.delite.runtime.profiler.PerformanceTimer.start(\"app\", false)")
      case ProfileStop() => emitValDef(sym, "ppl.delite.runtime.profiler.PerformanceTimer.stop(\"app\", false)")
      case _ => super.emitNode(sym, rhs)
    }
  }
}

/*
trait CudaGenLanguageOps extends CudaGenBase with BaseGenLanguageOps {
  val IR: LanguageOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = {
      rhs match {
        case _ => super.emitNode(sym, rhs)
     }
  }
}
*/
