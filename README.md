Run httpc.jar using:

1. java -cp httpc.jar httpc help

2. java -cp httpc.jar httpc help get

3. java -cp httpc.jar httpc help post

4. java -cp httpc.jar httpc get 'http://httpbin.org/get?course=networking&assignment=1'

5. java -cp httpc.jar httpc get -v "http://httpbin.org/get?course=networking&assignment=1"

6. java -cp httpc.jar httpc post -h Content-Type:application/json -d "{\"Assignment\": 1}" http://httpbin.org/post

7. java -cp httpc.jar httpc post -h Content-Type:application/json -f fileData.txt http://httpbin.org/post

8. java -cp httpc.jar httpc get -v 'http://httpbin.org/get?course=networking&assignment=1' -o hello.txt

---------------------

1. java -cp httpfs.jar httpfs --p 8007

2. java -cp httpfs.jar httpfs help

3. java -cp httpfs.jar httpfs get 'http://localhost:8007/foo' -v

4. java -cp httpfs.jar httpfs get 'http://localhost:8007/' -v

5. java -cp httpfs.jar httpfs get "http://localhost:8007/fileDoesNotExist" -v

6. java -cp httpfs.jar httpfs post "http://localhost:8007/111aaa.txt" -d "Write something" -v

---------------------

java -cp httpfs.jar httpfs --p 8007

router --port=3000 --drop-rate=0.2 --max-delay=10ms --seed=1
