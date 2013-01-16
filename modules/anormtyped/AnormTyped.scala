package anorm.typed 

object `package` {

  import reflect.macros.Context
  import language.experimental.macros

  def TypedSQL(sql: String) = macro TypedSQL_impl
 
  def TypedSQL_impl(c: Context)(sql: c.Expr[String]): c.Expr[Any] = {
    
    import java.sql._
    import c.universe._

    import com.typesafe.config._

    val config = ConfigFactory.load(ConfigFactory.parseFileAnySyntax {
      Option(
        System.getProperty("config.file")).map(f => new java.io.File(f)).getOrElse(new java.io.File("conf/application.conf")
      )
    })

    Class.forName(config.getString("db.default.driver"))
    val conn = DriverManager.getConnection(config.getString("db.default.url"), config.getString("db.default.user"), config.getString("db.default.password"))

    val sqlStatement = sql.tree match {
      case Literal(Constant(sql)) => sql.toString
      case _ => c.abort(c.enclosingPosition, "Typed SQL must be written as literal String")
    }

    val (parameters: Seq[String], results: Seq[(String, Boolean)]) = try {
      val preparedStatement = conn.prepareStatement(sqlStatement)

      def scalaize(name: String) = name match {
        case "java.lang.Integer" => "Int"
        case "java.lang.Long" => "Long"
        case "java.lang.String" => "String"
        case "java.lang.Float" => "Float"
        case "java.math.BigDecimal" => "BigDecimal"
        case name => name
      }

      (
        Option(preparedStatement.getParameterMetaData).filter(_.getParameterCount > 0).map { parameters =>
          for(p <- 1 to parameters.getParameterCount) yield scalaize(parameters.getParameterClassName(p))
        }.getOrElse(Nil),
        Option(preparedStatement.getMetaData).filter(_.getColumnCount > 0).map { results =>
          for(p <- 1 to results.getColumnCount) yield (scalaize(results.getColumnClassName(p)) -> (results.isNullable(p) != ResultSetMetaData.columnNoNulls))
        }.getOrElse(Nil)
      )

    } catch {
      case e: SQLException => {
        val DefaultError = """ERROR:\s*(.*?)\s*Position:\s*(\d+)""".r
        e.getMessage.trim.replace('\n', ' ') match {
          case DefaultError(msg, pos) => c.abort(sql.tree.pos.withPoint(sql.tree.pos.point + pos.toInt + 2), "SQL error: " + msg)
          case msg => c.abort(sql.tree.pos, "SQL error: " + msg)
        }
      }
      case e: Throwable => c.abort(c.enclosingPosition, "Unknown error: " + e.getMessage)
    } finally {
      conn.close()
    }

    /**
      - Something like:
      -
      - new TypedQuery1[Int,(String,Int)](
      -   query = new anorm.SqlQuery("select name, age from people where age > ?", TypedQuery.args(1)),
      -   rowParser = TypedQuery.autoParser[(String,Int)](2)
      - )
      **/   

    c.Expr[Any] {
      Apply(
        Select(

          // TypeQuery object
          New(
            AppliedTypeTree(
              Ident(newTypeName("TypedQuery" + parameters.size)), 

              // Parameter type
              parameters.toList.map( p =>

                Ident(newTypeName(p))

              ) :+ AppliedTypeTree(

                // Result type
                Ident(newTypeName(
                  results.size match {
                    case 0 => "Unit"
                    case 1 => results(0)._1
                    case x => "Tuple" + x
                  }
                )), 
                
                Option(results).filter(_.size > 1).map(_.toList).getOrElse(Nil).map {
                  case (t, true) => AppliedTypeTree(Ident(newTypeName("Option")), List(Ident(newTypeName(t))))
                  case (t, false) => Ident(newTypeName(t))
                }

              )
            )
          ), nme.CONSTRUCTOR), 

          List(
            Apply(Select(New(Ident(newTypeName("SqlQuery"))), nme.CONSTRUCTOR), 
              List(Literal(

                // Query
                Constant(sqlStatement)

              ), Apply(Select(Ident("TypedQuery"), newTermName("args")), List(Literal(

                // Nb of params
                Constant(parameters.size)

              ))))), 

            Apply(

              TypeApply(Select(Ident("TypedQuery"), newTermName("autoParser")), 

                List(

                  // RowParser type
                  AppliedTypeTree(

                    Ident(newTypeName(
                      results.size match {
                        case 0 => "Unit"
                        case 1 => results(0)._1
                        case x => "Tuple" + x
                      }
                    )), 
                    
                    Option(results).filter(_.size > 1).map(_.toList).getOrElse(Nil).map {
                      case (t, true) => AppliedTypeTree(Ident(newTypeName("Option")), List(Ident(newTypeName(t))))
                      case (t, false) => Ident(newTypeName(t))
                    }

                  )
                )

              ), 

              List(Literal(

                // Nb of results
                Constant(results.size)

              )
            )
          )
        )
      )
    }

  }

}

// -- API

trait TypedQuery[R] {

  def query: anorm.SqlQuery
  def rowParser: anorm.RowParser[R]

  private val raw = new anorm.ToStatement[Any] {
    def set(s: java.sql.PreparedStatement, index: Int, value: Any) = s.setObject(index, value)
  }

  def filledQuery(params: Any *) = {
    query.onParams(
      params.map(new anorm.ParameterValue(_, raw)): _*
    )
  }

}

object TypedQuery {

  import anorm._

    def autoParser[A](nb: Int): RowParser[A] = RowParser[A] { row =>
      val values = row.asList

      Success(
        {
          nb match {
            case 0 => Unit
            case 1 => (values(0))
            case 2 => (values(0), values(1))
            case 3 => (values(0), values(1), values(2))
            case 4 => (values(0), values(1), values(2), values(3))
            case 5 => (values(0), values(1), values(2), values(3), values(4))
            case 6 => (values(0), values(1), values(2), values(3), values(4), values(5))
          }
        }.asInstanceOf[A]
      )
    }

    def args(nb: Int) = (0 until nb).map(_.toString).toList

}

case class SqlResult[R](val sql: anorm.SimpleSql[anorm.Row], val rowParser: anorm.RowParser[R]) {

  def list()(implicit conn: java.sql.Connection): List[R] = {
    sql.as(rowParser *)
  }

  def single()(implicit conn: java.sql.Connection): R = {
    sql.as(rowParser single)
  }

  def singleOpt()(implicit conn: java.sql.Connection): Option[R] = {
    sql.as(rowParser singleOpt)
  }

  def execute()(implicit conn: java.sql.Connection): Unit = {
    sql.execute()
  }

  def map[X](f: R => X): SqlResult[X] = {
    new SqlResult(sql, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): SqlResult[X] = {
    new SqlResult(sql, rowParser.flatMap(f))
  }

}

case class TypedQuery0[R](val query: anorm.SqlQuery, val rowParser: anorm.RowParser[R]) extends TypedQuery[R] with (() => SqlResult[R]) {

  def apply() = {
    new SqlResult(query, rowParser)
  }

  def map[X](f: R => X): TypedQuery0[X] = {
    new TypedQuery0(query, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): TypedQuery0[X] = {
    new TypedQuery0(query, rowParser.flatMap(f))
  }

}

case class TypedQuery1[A,R](val query: anorm.SqlQuery, val rowParser: anorm.RowParser[R]) extends TypedQuery[R] with ((A) => SqlResult[R]) {

  def apply(a: A) = {
    new SqlResult(filledQuery(a), rowParser)
  }

  def map[X](f: R => X): TypedQuery1[A,X] = {
    new TypedQuery1(query, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): TypedQuery1[A,X] = {
    new TypedQuery1(query, rowParser.flatMap(f))
  }

}

case class TypedQuery2[A,B,R](val query: anorm.SqlQuery, val rowParser: anorm.RowParser[R]) extends TypedQuery[R] with ((A,B) => SqlResult[R]) {

  def apply(a: A, b: B) = {
    new SqlResult(filledQuery(a, b), rowParser)
  }

  def map[X](f: R => X): TypedQuery2[A,B,X] = {
    new TypedQuery2(query, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): TypedQuery2[A,B,X] = {
    new TypedQuery2(query, rowParser.flatMap(f))
  }

}

case class TypedQuery3[A,B,C,R](val query: anorm.SqlQuery, val rowParser: anorm.RowParser[R]) extends TypedQuery[R] with ((A,B,C) => SqlResult[R]) {

  def apply(a: A, b: B, c: C) = {
    new SqlResult(filledQuery(a, b, c), rowParser)
  }

  def map[X](f: R => X): TypedQuery3[A,B,C,X] = {
    new TypedQuery3(query, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): TypedQuery3[A,B,C,X] = {
    new TypedQuery3(query, rowParser.flatMap(f))
  }

} 

case class TypedQuery4[A,B,C,D,R](val query: anorm.SqlQuery, val rowParser: anorm.RowParser[R]) extends TypedQuery[R] with ((A,B,C,D) => SqlResult[R]) {

  def apply(a: A, b: B, c: C, d: D) = {
    new SqlResult(filledQuery(a, b, c, d), rowParser)
  }

  def map[X](f: R => X): TypedQuery4[A,B,C,D,X] = {
    new TypedQuery4(query, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): TypedQuery4[A,B,C,D,X] = {
    new TypedQuery4(query, rowParser.flatMap(f))
  }
 
}

case class TypedQuery5[A,B,C,D,E,R](val query: anorm.SqlQuery, val rowParser: anorm.RowParser[R]) extends TypedQuery[R] with ((A,B,C,D,E) => SqlResult[R]) {

  def apply(a: A, b: B, c: C, d: D, e: E) = {
    new SqlResult(filledQuery(a, b, c, d, e), rowParser)
  }

  def map[X](f: R => X): TypedQuery5[A,B,C,D,E,X] = {
    new TypedQuery5(query, rowParser.map(f))
  }

  def flatMap[X](f: R => anorm.RowParser[X]): TypedQuery5[A,B,C,D,E,X] = {
    new TypedQuery5(query, rowParser.flatMap(f))
  }
 
}