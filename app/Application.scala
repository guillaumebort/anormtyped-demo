package controllers

import play.api._
import play.api.mvc._

import play.api.data._
import play.api.data.Forms._

import play.api.i18n._

import play.api.db._

object Application extends Controller {

  import anorm._
  import anorm.typed._

  import Play.current

  // Typed queries

  val allCountries = TypedSQL(
    """
      select code, name, continent, indepyear, population, headofstate
      from country
    """
  ).map(Country.tupled)

  val findPopulationByCode = TypedSQL(
    """
      select population from country where code = ?
    """
  )

  val findAllCitiesInContinent = TypedSQL(
    """
      select city.id, city.name from city 
      join country on city.countrycode = country.code 
      where country.continent like ?
    """ 
  )

  val insertCity = TypedSQL(
    """
      insert into city (name, countrycode) 
      values (?,?)
    """
  )

  val deleteCity = TypedSQL(
    """
      delete from city where id = ?
    """
  )

  // A dummy model class

  case class Country(
    val code: String,
    val name: String,
    val continent: String,
    val indepyear: Option[Int],
    val population: Int,
    val headofstate: Option[String]
  )

  // Play actions
   
  def index = Action {
    DB.withConnection { implicit c =>
      Ok(
        <html>
          <body>

            <h1>Some countries:</h1>
            <table>
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Continent</th>
                  <th>Indep. year</th>
                </tr>
              </thead>
              {
                allCountries().list.map { country =>
                  <tr>
                    <td>{country.code}</td>
                    <td>{country.name}</td>
                    <td>{country.continent}</td>
                    <td>{country.indepyear.getOrElse(<em>Not specified</em>)}</td>
                  </tr>
                }
              }
            </table>

            <h2>France population:</h2>
            <p>
              {findPopulationByCode("FRA").singleOpt.getOrElse(<em>Unknown</em>)}
            </p>

            <h2>Cities in Europe:</h2>
            <ul>
              {
                findAllCitiesInContinent("Europe").list.map {
                  case (id, city) => 
                    <li>{city} <form action={routes.Application.delete(id).url} method="POST" style="display: inline"><input type="submit" value="delete"/></form></li>
                }
              }
            </ul>

            <h2>Add a city</h2>
            <form action={routes.Application.create.url} method="POST">
              <p>
                <label for="name">Name</label>
                <input type="text" name="name" id="name"/> *
              </p>
              <p>
                <label for="countrycode">Country code</label>
                <input type="text" name="countrycode" id="countrycode"/> *
              </p>
              <p>
                <input type="submit" value="Create city"/>
              </p>
            </form>

          </body>
        </html>
      ).as(HTML)
    }
  }

  def create = Action { implicit request =>
    Form(
      tuple(
        "name" -> nonEmptyText,
        "countrycode" -> nonEmptyText
      )
    ).bindFromRequest.fold(

      // Errors
      oops => BadRequest("All fields are required"),

      // Ok, create the City
      data => DB.withConnection { implicit c =>
        insertCity.tupled(data).execute
        Redirect(routes.Application.index)
      }

    )
  }

  def delete(id: Int) = Action {
    DB.withConnection { implicit c =>
      deleteCity(id).execute
      Redirect(routes.Application.index)
    }
  }
  
}
