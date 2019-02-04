Run httpc.jar using:

1. java -cp httpc.jar httpc help

2. java -cp httpc.jar httpc help get

3. java -cp httpc.jar httpc help post

4. java -cp httpc.jar httpc get 'http://httpbin.org/get?course=networking&assignment=1'

5. java -cp httpc.jar httpc get -v "http://httpbin.org/get?course=networking&assignment=1"

6. java -cp httpc.jar httpc post -h Content-Type:application/json -d "{\"Assignment\": 1}" http://httpbin.org/post

7. java -cp httpc.jar httpc post -h Content-Type:application/json -f fileData.txt http://httpbin.org/post

8. java -cp httpc.jar httpc get -v 'http://httpbin.org/get?course=networking&assignment=1' -o hello.txt

