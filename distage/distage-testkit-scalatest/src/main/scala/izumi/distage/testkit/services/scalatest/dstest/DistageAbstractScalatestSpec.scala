package izumi.distage.testkit.services.scalatest.dstest

import distage.{TagK, TagKK}
import izumi.distage.constructors.HasConstructor
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestId, TestMeta}
import izumi.distage.testkit.services.dstest._
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec._
import izumi.distage.testkit.services.{DISyntaxBIOBase, DISyntaxBase}
import izumi.functional.bio.BIOLocal
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer, unused}
import izumi.fundamentals.reflection.Tags.TagK3
import izumi.logstage.api.{IzLogger, Log}
import org.scalactic.source
import org.scalatest.Assertion
import org.scalatest.distage.TestCancellation
import org.scalatest.verbs.{CanVerb, MustVerb, ShouldVerb, StringVerbBlockRegistration}

import scala.language.implicitConversions

trait WithSingletonTestRegistration[F[_]] extends AbstractDistageSpec[F] {
  private[this] lazy val firstRegistration: Boolean = DistageTestsRegistrySingleton.registerSuite[F](this.getClass.getName)

  override def registerTest(function: ProviderMagnet[F[_]], env: TestEnvironment, pos: CodePosition, id: TestId): Unit = {
    if (firstRegistration) {
      DistageTestsRegistrySingleton.register[F](DistageTest(function, env, TestMeta(id, pos, System.identityHashCode(function).toLong)))
    }
  }
}

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait DistageAbstractScalatestSpec[F[_]]
  extends ShouldVerb with MustVerb with CanVerb
    with DistageTestEnv
    with WithSingletonTestRegistration[F] {
  this: AbstractDistageSpec[F] =>

  final protected lazy val testEnv: TestEnvironment = makeTestEnv()

  protected def testEnvLogger: IzLogger = IzLogger(Log.Level.Info)("phase" -> "loader")

  protected def config: TestConfig = TestConfig.forSuite(this.getClass)
  protected def makeTestEnv(): TestEnvironment = loadEnvironment(testEnvLogger, config)

  protected def distageSuiteName: String = getSimpleNameOfAnObjectsClass(this)
  protected def distageSuiteId: String = this.getClass.getName

  //
  private[distage] var context: Option[SuiteContext] = None

  implicit val subjectRegistrationFunction1: StringVerbBlockRegistration = new StringVerbBlockRegistration {
    override def apply(left: String, verb: String, @unused pos: source.Position, f: () => Unit): Unit = {
      registerBranch(left, verb, f)
    }
  }

  protected def registerBranch(description: String, verb: String, fun: () => Unit): Unit = {
    this.context = Some(SuiteContext(description, verb))
    fun()
    this.context = None
  }

  protected implicit def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[F] = {
    new DSWordSpecStringWrapper(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }
}

object DistageAbstractScalatestSpec {
  final case class SuiteContext(left: String, verb: String) {
    def toName(name: String): String = {
      Seq(left, verb, name).mkString(" ")
    }
  }

  trait LowPriorityIdentityOverloads[F[_]] extends DISyntaxBase[F] {
    def in(function: ProviderMagnet[Unit])(implicit pos: CodePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    def in(function: ProviderMagnet[Assertion])(implicit pos: CodePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit, d3: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    def in(value: => Unit)(implicit pos: CodePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit): Unit = {
      takeAny(() => value, pos.get)
    }

    def in(value: => Assertion)(implicit pos: CodePositionMaterializer, d1: DummyImplicit, d2: DummyImplicit, d3: DummyImplicit): Unit = {
      takeAny(() => value, pos.get)
    }

    def skip(@unused value: => Any)(implicit pos: CodePositionMaterializer): Unit = {
      takeFunIO(cancel, pos.get)
    }

    private def cancel(F: DIEffect[F]): F[Nothing] = {
      F.maybeSuspend(cancelNow())
    }

    private def cancelNow(): Nothing = {
      TestCancellation.cancel(Some("test skipped!"), None, 1)
    }
  }

  class DSWordSpecStringWrapper[F[_]](
                                       context: Option[SuiteContext],
                                       suiteName: String,
                                       suiteId: String,
                                       testname: String,
                                       reg: TestRegistration[F],
                                       env: TestEnvironment,
                                     )(
                                       implicit override val tagMonoIO: TagK[F],
                                     ) extends DISyntaxBase[F] with LowPriorityIdentityOverloads[F] {

    override protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): Unit = {
      val id = TestId(
        context.map(_.toName(testname)).getOrElse(testname),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(function, env, pos, id)
    }

    def in(function: ProviderMagnet[F[Unit]])(implicit pos: CodePositionMaterializer): Unit = {
      takeIO(function, pos.get)
    }

    def in(function: ProviderMagnet[F[Assertion]])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeIO(function, pos.get)
    }

    def in(value: => F[Unit])(implicit pos: CodePositionMaterializer): Unit = {
      takeIO(() => value, pos.get)
    }

    def in(value: => F[Assertion])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeIO(() => value, pos.get)
    }
  }

  class DSWordSpecStringWrapper2[F[+_, +_]](
                                             context: Option[SuiteContext],
                                             suiteName: String,
                                             suiteId: String,
                                             testname: String,
                                             reg: TestRegistration[F[Throwable, ?]],
                                             env: TestEnvironment,
                                           )(
                                             implicit override val tagBIO: TagKK[F],
                                             implicit override val tagMonoIO: TagK[F[Throwable, ?]],
                                           ) extends DISyntaxBIOBase[F] with LowPriorityIdentityOverloads[F[Throwable, ?]] {

    override protected def takeIO(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): Unit = {
      val id = TestId(
        context.map(_.toName(testname)).getOrElse(testname),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(fAsThrowable, env, pos, id)
    }

    def in(function: ProviderMagnet[F[_, Unit]])(implicit pos: CodePositionMaterializer): Unit = {
      takeBIO(function, pos.get)
    }

    def in(function: ProviderMagnet[F[_, Assertion]])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function, pos.get)
    }

    def in(value: => F[_, Unit])(implicit pos: CodePositionMaterializer): Unit = {
      takeBIO(() => value, pos.get)
    }

    def in(value: => F[_, Assertion])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value, pos.get)
    }
  }

  class DSWordSpecStringWrapper3[F[-_, +_, +_]: TagK3](
                                             context: Option[SuiteContext],
                                             suiteName: String,
                                             suiteId: String,
                                             testname: String,
                                             reg: TestRegistration[F[Any, Throwable, ?]],
                                             env: TestEnvironment,
                                           )(
                                             implicit override val tagBIO: TagKK[F[Any, ?, ?]],
                                             implicit override val tagMonoIO: TagK[F[Any, Throwable, ?]]
                                           ) extends DISyntaxBIOBase[F[Any, +?, +?]] with LowPriorityIdentityOverloads[F[Any, Throwable, ?]] {

    override protected def takeIO(fAsThrowable: ProviderMagnet[F[Any, Throwable, _]], pos: CodePosition): Unit = {
      val id = TestId(
        context.map(_.toName(testname)).getOrElse(testname),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(fAsThrowable, env, pos, id)
    }

    def in[R: HasConstructor](function: ProviderMagnet[F[R, _, Unit]])(implicit pos: CodePositionMaterializer): Unit = {
      takeBIO(function.zip(HasConstructor[R]).map2(ProviderMagnet.identity[BIOLocal[F]]) {
        case ((eff, r), f) => f.provide(eff)(r)
      }, pos.get)
    }

    def in[R: HasConstructor](function: ProviderMagnet[F[R, _, Assertion]])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function.zip(HasConstructor[R]).map2(ProviderMagnet.identity[BIOLocal[F]]) {
        case ((eff, r), f) => f.provide(eff)(r)
      }, pos.get)
    }

    def in[R: HasConstructor](value: => F[R, _, Unit])(implicit pos: CodePositionMaterializer): Unit = {
      takeBIO(ProviderMagnet.identity[BIOLocal[F]].map2(HasConstructor[R])(_.provide(value)(_)), pos.get)
    }

    def in[R: HasConstructor](value: => F[R, _, Assertion])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(ProviderMagnet.identity[BIOLocal[F]].map2(HasConstructor[R])(_.provide(value)(_)), pos.get)
    }

    def in(function: ProviderMagnet[F[Any, _, Unit]])(implicit pos: CodePositionMaterializer): Unit = {
      takeBIO(function, pos.get)
    }

    def in(function: ProviderMagnet[F[Any, _, Assertion]])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(function, pos.get)
    }

    def in(value: => F[Any, _, Unit])(implicit pos: CodePositionMaterializer): Unit = {
      takeBIO(() => value, pos.get)
    }

    def in(value: => F[Any, _, Assertion])(implicit pos: CodePositionMaterializer, d1: DummyImplicit): Unit = {
      takeBIO(() => value, pos.get)
    }
  }

//  abstract class StringVerbBlockRegistration {
//    def apply(string: String, verb: String, pos: source.Position, block: () => Unit): Unit
//  }

  def getSimpleNameOfAnObjectsClass(o: AnyRef): String = stripDollars(parseSimpleName(o.getClass.getName))

  // [bv: this is a good example of the expression type refactor. I moved this from SuiteClassNameListCellRenderer]
  // this will be needed by the GUI classes, etc.
  def parseSimpleName(fullyQualifiedName: String): String = {

    val dotPos = fullyQualifiedName.lastIndexOf('.')

    // [bv: need to check the dotPos != fullyQualifiedName.length]
    if (dotPos != -1 && dotPos != fullyQualifiedName.length)
      fullyQualifiedName.substring(dotPos + 1)
    else
      fullyQualifiedName
  }

  // This attempts to strip dollar signs that happen when using the interpreter. It is quite fragile
  // and already broke once. In the early days, all funky dollar sign encrusted names coming out of
  // the interpreter started with "line". Now they don't, but in both cases they seemed to have at
  // least one "$iw$" in them. So now I leave the string alone unless I see a "$iw$" in it. Worst case
  // is sometimes people will get ugly strings coming out of the interpreter. -bv April 3, 2012
  def stripDollars(s: String): String = {
    val lastDollarIndex = s.lastIndexOf('$')
    if (lastDollarIndex < s.length - 1)
      if (lastDollarIndex == -1 || !s.contains("$iw$")) s else s.substring(lastDollarIndex + 1)
    else {
      // The last char is a dollar sign
      val lastNonDollarChar = s.reverse.find(_ != '$')
      lastNonDollarChar match {
        case None => s
        case Some(c) => {
          val lastNonDollarIndex = s.lastIndexOf(c.toInt)
          if (lastNonDollarIndex == -1) s
          else stripDollars(s.substring(0, lastNonDollarIndex + 1))
        }
      }
    }
  }
}
