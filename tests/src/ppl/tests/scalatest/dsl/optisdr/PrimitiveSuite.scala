package ppl.tests.scalatest.dsl.optisdr

import ppl.delite.framework.DeliteApplication
import ppl.dsl.optisdr.{OptiSDRApplicationRunner, OptiSDRApplication}
import ppl.tests.scalatest._

/* Testing OptiSDR primitives functionality
 *
 * author:  Michael Wu (michaelmwu@stanford.edu)
 * created: 3/04/12
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 *
 */

object ComplexTestRunner extends DeliteTestRunner with OptiSDRApplicationRunner with ComplexTest
trait ComplexTest extends DeliteTestModule with OptiSDRApplication {
  def main() = {
    val a = Complex(1, 1)
    val b = Complex(2, -2)
    
    val c = a + b
    
    val d = a.conj
    
    val e = a * d
    
    val f = b.real + b.imag
  }
}

object UIntTestRunner extends DeliteTestRunner with OptiSDRApplicationRunner with UIntTest
trait UIntTest extends DeliteTestModule with OptiSDRApplication {
  def main() = {
    val a = UInt(1)
    val b = UInt(2)
    
    val c = a * b
  }
}

class PrimitiveSuite extends DeliteSuite {
  def testComplexOps() { compileAndTest(ComplexTestRunner) }
  
  def testUIntOps() { compileAndTest(UIntTestRunner) }
}

